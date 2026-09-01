package dev.sweety.netty.server;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.feature.batch.Batch;
import dev.sweety.netty.packet.Packer;
import dev.sweety.netty.server.backend.BackendNode;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.math.list.BlockingDeque;
import dev.sweety.thread.ThreadManager;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.packet.internal.ForwardData;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.queue.OrderedResponseQueue;
import dev.sweety.netty.packet.queue.PacketContext;
import dev.sweety.netty.packet.queue.PacketReorder;

import dev.sweety.netty.server.pool.IDynamicBackendNodePool;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LoadBalancerServer<Node extends BackendNode> extends Server {
    private static final int MAX_PENDING_PACKETS = Integer
            .parseInt(System.getenv().getOrDefault("LB_MAX_PENDING", "10000"));

    protected final SimpleLogger logger = SimpleLogger.of(LoadBalancerServer.class);

    protected final IDynamicBackendNodePool<Node> backendPool;

    protected final ThreadManager queueScheduler = new ThreadManager("queue-scheduler");

    private final ExecutorService drain_executor = ThreadUtil.singleThreadScheduler("lb-drain");
    private final AtomicBoolean draining = new AtomicBoolean(false);
    protected final BlockingDeque<PacketContext> pendingPackets = new BlockingDeque<>();
    private final Disruptor<IngressEvent> ingressDisruptor;
    private final RingBuffer<IngressEvent> ingressRingBuffer;

    protected final TransactionManager transactionManager = new TransactionManager(this);
    protected final PacketReorder reorder = new PacketReorder();

    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(ThreadUtil.factory("health-check"));

    private final Batch.Constructor constructor;

    public <T extends IDynamicBackendNodePool<Node>> LoadBalancerServer(String host, int port, T backendPool,
                                                                        PacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.backendPool = backendPool;
        this.constructor = (id, ts, data) -> {
            try {
                return packetRegistry.constructPacket(id, ts, ((PacketBuffer) data).getBytes());
            } catch (Exception e) {
                return null;
            }
        };

        this.healthCheckExecutor.scheduleAtFixedRate(this::checkHealth, 30, 30, TimeUnit.SECONDS);

        this.logger.profile("init")
                .info("LoadBalancerServer started on " + host + ":" + port)
                .info("Waiting for connections...");

        final boolean useDisruptor = Boolean
                .parseBoolean(System.getenv().getOrDefault("LB_USE_DISRUPTOR_INGRESS", "true"));

        if (useDisruptor) {
            this.ingressDisruptor = new Disruptor<>(
                    IngressEvent::new,
                    8192,
                    ThreadUtil.factory("lb-ingress"),
                    ProducerType.MULTI,
                    new LiteBlockingWaitStrategy());

            final EventHandler<IngressEvent> handler = (event, sequence, endOfBatch) -> {
                event.useAndInvalidate(this.pendingPackets::offerLast);

                if (endOfBatch)
                    this.drainPending();
            };
            this.ingressDisruptor.handleEventsWith(handler);
            this.ingressRingBuffer = this.ingressDisruptor.start();
            this.logger.profile("init").info("Disruptor ingress enabled (ring=8192)");
        } else {
            this.ingressDisruptor = null;
            this.ingressRingBuffer = null;
            this.logger.profile("init").warn("Disruptor ingress disabled; using direct enqueue");
        }
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (this.pendingPackets.size() >= MAX_PENDING_PACKETS) {
            this.logger.profile("backpressure")
                    .warn("Dropping packet due to pending queue saturation: " + this.pendingPackets.size());
            return;
        }

        Node node = this.backendPool.get(ctx);
        if (node == null) {
            node = this.resolveNodeByChannel(ctx.channel());
            if (node != null) {
                this.backendPool.add(ctx, node);
                this.logger.profile("recovery")
                        .warn("Recovered node mapping by channel for " + Messenger.address(ctx.channel()));
            }
        }
        if (node == null) {
            final SocketAddress addr = ctx.channel().remoteAddress();
            if (addr instanceof InetSocketAddress inet) {
                this.logger.profile("recovery")
                        .warn("Node missing for " + Messenger.address(ctx.channel()) + ", forcing registration");
                node = this.backendPool.createAndAdd(this, ctx, inet);
            }
        }
        if (node != null)
            node.onPacketReceive(ctx, packet);

        if (!(packet instanceof InternalPacket internal)) {
            if (node != null && node.handled(packet))
                return;
            logger.info("(non-internal), Received", packet.name(), "from", Messenger.address(ctx.channel()),
                    "(node:" + (node == null ? null : node.typeName()) + ")");
            return;
        }

        if (internal.hasRequest() && internal.isFireAndForget()) {
            final Node backend = this.next(internal, ctx);
            if (backend != null) {
                final ChannelHandlerContext backendCtx = this.backendPool.context(backend);
                if (backendCtx != null) {
                    backend.forward(internal);
                    sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                        backend.decrementInFlight();
                        if (t != null) this.backendPool.remove(backend);
                    });
                    return;
                }
            }
        }

        final OrderedResponseQueue queue = this.reorder.enqueue(ctx, this::sendPacket);
        long sequenceId = queue.nextSequenceId();

        final PacketContext packetContext = new PacketContext(packet, ctx, sequenceId);
        if (this.ingressRingBuffer != null) {
            final long seq = this.ingressRingBuffer.next();
            try {
                this.ingressRingBuffer.get(seq).context = packetContext;
            } finally {
                this.ingressRingBuffer.publish(seq);
            }
            return;
        }
        this.pendingPackets.offerLast(packetContext);
        this.drainPending();
    }

    public void checkHealth() {
        for (Node node : backendPool.pool()) {
            if (node.context() == null || !node.context().channel().isActive()) {
                this.logger.profile("health").warn("Node " + node.typeName() + " is unreachable");
            }
        }
    }

    private Node resolveNodeByChannel(Channel channel) {
        for (Node candidate : this.backendPool.pool()) {
            final ChannelHandlerContext mapped = this.backendPool.context(candidate);
            if (mapped == null) {
                continue;
            }
            if (mapped.channel() == channel) {
                return candidate;
            }
        }
        return null;
    }

    private volatile boolean useThreadManager = false;

    private static final long REQUEST_TIMEOUT_SECONDS = Long
            .parseLong(System.getenv().getOrDefault("HUB_REQUEST_TIMEOUT_SECONDS", "180"));

    public static long requestTimeout() {
        return REQUEST_TIMEOUT_SECONDS * 1000L;
    }

    public void useThreadManager() {
        this.useThreadManager = true;
    }

    public void drainPending() {
        if (this.draining.compareAndSet(false, true)) {
            this.drain_executor.execute(() -> {
                try {
                    this.drainPendingInternal();
                } finally {
                    this.draining.set(false);
                    if (!this.pendingPackets.isEmpty()) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        this.drainPending();
                    }
                }
            });
            return;
        }
        if (this.useThreadManager) {
            this.queueScheduler.fireAndForget(t -> null);
        }
    }

    public Node next(InternalPacket packet, ChannelHandlerContext ctx) {
        return this.backendPool.next(packet, ctx);
    }

    private void drainPendingInternal() {
        if (this.pendingPackets.isEmpty())
            return;
        PacketContext pq;
        int skipped = 0;
        final int maxSkips = this.pendingPackets.size();

        while ((pq = this.pendingPackets.pollFirst()) != null) {
            final Packet packet = pq.packet();
            final ChannelHandlerContext ctx = pq.ctx();
            final long sequenceId = pq.sequenceId();

            if (!(packet instanceof InternalPacket internal)) {
                continue;
            }
            if (!internal.hasRequest() && !internal.hasResponse()) {
                continue;
            }

            if (internal.hasResponse()) {
                complete(internal, ctx);
                continue;
            }

            final Node backend = this.next(internal, ctx);

            if (backend == null) {
                this.pendingPackets.offerLast(pq);
                skipped++;
                if (skipped >= maxSkips) {
                    this.logger.profile("queue")
                            .warn("No backend available for " + skipped + " packets, will retry after backoff");
                    break;
                }
                continue;
            }
            skipped = 0;

            ChannelHandlerContext backendCtx = this.backendPool.context(backend);
            if (backendCtx == null) {
                this.pendingPackets.offerLast(pq);
                skipped++;
                if (skipped >= maxSkips) {
                    this.logger.profile("queue")
                            .warn("Backend context missing, re-queued " + skipped + " packets, will retry after backoff");
                    break;
                }
                continue;
            }

            if (internal.isFireAndForget()) {
                backend.forward(internal);
                sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                    backend.decrementInFlight();
                    if (t != null) {
                        this.backendPool.remove(backend);
                        this.drainPending();
                    }
                });
                continue;
            }

            final OrderedResponseQueue responseQueue = this.reorder.find(ctx.channel().remoteAddress());

            this.transactionManager.registerRequest(internal, requestTimeout()).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    backend.timeout(internal.getRequestId());
                    this.logger.profile("transaction")
                            .error(internal.requestCode(), throwable.getMessage());

                    if (responseQueue != null && sequenceId >= 0)
                        responseQueue.complete(sequenceId, Packer.EMPTY());
                    return;
                }
                try {
                    final Packet[] responses = handleResponses(ctx, internal, response);

                    ForwardData request = internal.getRequest();
                    ForwardData responseForward = new ForwardData(
                            request.receiverId(),
                            request.senderId(),
                            request.context(),
                            packetRegistry()::getPacketId,
                            responses);
                    InternalPacket responsePacket = new InternalPacket(internal.getRequestId(), responseForward);

                    Messenger.safeRun(ctx, c -> sendPacket(c, responsePacket));
                } catch (Throwable relayError) {
                    this.logger.profile("response-relay")
                            .error("Failed relaying response " + internal.requestCode(), relayError);
                } finally {
                    internal.release();
                }
            });

            backend.forward(internal);
            sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                backend.decrementInFlight();
                if (t != null) {
                    this.transactionManager.failRequest(internal.getRequestId(), t);
                    this.backendPool.remove(backend);
                    this.logger.profile("queue")
                            .warn("Send to backend failed, removed stale node " + backend.typeName()
                                    + " request=" + internal.requestCode() + " error=" + t.getClass().getSimpleName());
                    this.drainPending();
                }
            });
        }
    }

    protected Packet[] handleResponses(ChannelHandlerContext ctx, InternalPacket internal, ForwardData response) {
        return response.decode(this.constructor);
    }

    public void complete(InternalPacket internal, ChannelHandlerContext ctx) {
        if (!this.transactionManager.completeResponse(internal, ctx)) {
            final ForwardData response = internal.getResponse();
            this.logger.profile("expired")
                    .warn(internal.requestCode(), "from", response.senderId(), "to", response.receiverId());
        }
    }

    @Override
    public void stop() {
        if (this.ingressDisruptor != null) {
            this.ingressDisruptor.shutdown();
        }
        super.stop();
        this.healthCheckExecutor.shutdownNow();
        this.drain_executor.shutdownNow();
        this.transactionManager.shutdown();
        this.reorder.shutdown();
        this.queueScheduler.shutdown();
    }

    private static final class IngressEvent {
        private PacketContext context;

        public void useAndInvalidate(Consumer<PacketContext> action) {
            if (this.context != null) {
                action.accept(this.context);
                this.context = null;
            }
        }
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (!AutoReconnect.exception(throwable))
            this.logger.profile("exception").error(throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        final Channel channel = ctx.channel();
        this.logger.profile("connect").info(Messenger.address(channel));
        final SocketAddress address = channel.remoteAddress();
        super.addClient(ctx, address);

        this.backendPool.createAndAdd(this, ctx, (InetSocketAddress) address);

        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        final SocketAddress addr = ctx.channel().remoteAddress();
        this.reorder.remove(addr);
        final Node node = this.backendPool.remove(ctx);
        this.logger.profile("disconnect").info(Messenger.address(ctx.channel()));
        if (node != null) {
            node.disconnect();
        }
        promise.setSuccess();
    }

    public SimpleLogger logger() {
        return logger;
    }

    public IDynamicBackendNodePool<Node> backendPool() {
        return backendPool;
    }

    public ThreadManager queueScheduler() {
        return queueScheduler;
    }

    public ExecutorService drain_executor() {
        return drain_executor;
    }

    public AtomicBoolean draining() {
        return draining;
    }

    public BlockingDeque<PacketContext> pendingPackets() {
        return pendingPackets;
    }

    public Disruptor<IngressEvent> ingressDisruptor() {
        return ingressDisruptor;
    }

    public RingBuffer<IngressEvent> ingressRingBuffer() {
        return ingressRingBuffer;
    }

    public TransactionManager transactionManager() {
        return transactionManager;
    }

    public PacketReorder reorder() {
        return reorder;
    }

    public Batch.Constructor constructor() {
        return constructor;
    }
}
