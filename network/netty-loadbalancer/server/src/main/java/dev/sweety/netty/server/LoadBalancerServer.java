package dev.sweety.netty.server;

import dev.sweety.color.AnsiColor;
import dev.sweety.netty.packet.Packer;
import dev.sweety.netty.server.backend.BackendNode;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.math.function.TriFunction;
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
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.awt.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LoadBalancerServer<Node extends BackendNode> extends Server {
    private static final int MAX_PENDING_PACKETS = Integer
            .parseInt(System.getenv().getOrDefault("LB_MAX_PENDING", "10000"));

    protected final SimpleLogger logger = new SimpleLogger(LoadBalancerServer.class);

    protected final IDynamicBackendNodePool<Node> backendPool;

    protected final ThreadManager queueScheduler = new ThreadManager("queue-scheduler");

    private final ExecutorService drain_executor = ThreadUtil.singleThreadScheduler("lb-drain");
    private final AtomicBoolean draining = new AtomicBoolean(false);
    protected final BlockingDeque<PacketContext> pendingPackets = new BlockingDeque<>();
    private final Disruptor<IngressEvent> ingressDisruptor;
    private final RingBuffer<IngressEvent> ingressRingBuffer;

    protected final TransactionManager transactionManager = new TransactionManager(this);
    protected final PacketReorder reorder = new PacketReorder();

    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public <T extends IDynamicBackendNodePool<Node>> LoadBalancerServer(String host, int port, T backendPool,
                                                                        IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.backendPool = backendPool;
        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this.logger);

        this.logger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76)))
                .info("LoadBalancerServer started on " + host + ":" + port)
                .info("Waiting for connections...").pop();

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
            this.logger.push("<init>").info("Disruptor ingress enabled (ring=8192)").pop();
        } else {
            this.ingressDisruptor = null;
            this.ingressRingBuffer = null;
            this.logger.push("<init>").warn("Disruptor ingress disabled; using direct enqueue").pop();
        }
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (this.pendingPackets.size() >= MAX_PENDING_PACKETS) {
            this.logger.push("backpressure", AnsiColor.RED_BRIGHT)
                    .warn("Dropping packet due to pending queue saturation: " + this.pendingPackets.size())
                    .pop();
            packet.release();
            return;
        }

        Node node = this.backendPool.get(ctx);
        if (node == null) {
            // Netty can surface different handler contexts for the same channel; recover by
            // channel identity and remap this context so future lookups are stable.
            node = this.resolveNodeByChannel(ctx.channel());
            if (node != null) {
                this.backendPool.add(ctx, node);
                this.logger.push("recovery")
                        .warn("Recovered node mapping by channel for " + Messenger.address(ctx.channel())).pop();
            }
        }
        if (node == null) {
            // Fail-safe: force node creation if join() didn't register it or the node was
            // evicted
            final SocketAddress addr = ctx.channel().remoteAddress();
            if (addr instanceof InetSocketAddress inet) {
                this.logger.push("recovery")
                        .warn("Node missing for " + Messenger.address(ctx.channel()) + ", forcing registration").pop();
                node = this.backendPool.createAndAdd(this, ctx, inet);
            }
        }
        if (node != null)
            node.onPacketReceive(ctx, packet);

        if (!(packet instanceof InternalPacket internal)) {
            if (node != null && node.handled(packet))
                return;
            logger.info("(non-internal), Received", packet.name(), "from " + Messenger.address(ctx.channel())
                    + " (node:" + (node == null ? null : node.typeName()) + ")");
            return;
        }

        // Fast path: fire-and-forget packets bypass the queue entirely,
        // keeping it clear for transactions (e.g. license validation).
        if (internal.hasRequest() && internal.isFireAndForget()) {
            final Node backend = this.next(internal, ctx);
            if (backend != null) {
                final ChannelHandlerContext backendCtx = this.backendPool.context(backend);
                if (backendCtx != null) {
                    internal.retain().rewind();
                    backend.forward(internal);
                    sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                        internal.release();
                        backend.decrementInFlight();
                        if (t != null) this.backendPool.remove(backend);
                    });
                    return;
                }
            }
            // Fall through to normal queue if direct send failed
        }

        final OrderedResponseQueue queue = this.reorder.enqueue(ctx, this::sendPacket);
        long sequenceId = queue.nextSequenceId();

        final PacketContext packetContext = new PacketContext(packet.retain().rewind(), ctx, sequenceId);
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
                packet.release();
                continue;
            }
            if (!internal.hasRequest() && !internal.hasResponse()) {
                packet.release();
                continue;
            }

            // Process responses immediately — they don't need a backend node,
            // just the transaction manager to match the requestId callback.
            // This prevents responses (e.g. license validation) from getting
            // stuck behind unroutable requests under high load.
            if (internal.hasResponse()) {
                complete(internal, ctx);
                packet.release();
                continue;
            }

            final Node backend = this.next(internal, ctx);

            if (backend == null) {
                this.pendingPackets.offerLast(pq);
                skipped++;
                if (skipped >= maxSkips) {
                    this.logger.push("queue", AnsiColor.RED_BRIGHT)
                            .warn("No backend available for " + skipped + " packets, will retry after backoff").pop();
                    break;
                }
                continue;
            }
            skipped = 0;

            ChannelHandlerContext backendCtx = this.backendPool.context(backend);
            if (backendCtx == null) {
                // Do not register a transaction for packets that were not sent.
                this.pendingPackets.offerLast(pq);
                skipped++;
                if (skipped >= maxSkips) {
                    this.logger.push("queue", AnsiColor.RED_BRIGHT)
                            .warn("Backend context missing, re-queued " + skipped + " packets, will retry after backoff")
                            .pop();
                    break;
                }
                continue;
            }

            // Fire-and-forget: just forward to backend, no transaction, no response expected
            if (internal.isFireAndForget()) {
                backend.forward(internal);
                sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                    packet.release();
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
                    this.logger.push("transaction", AnsiColor.RED_BRIGHT)
                            .error(internal.requestCode(), throwable.getMessage()).pop();

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
                            packetRegistry()::getPacketId,
                            responses);
                    InternalPacket responsePacket = new InternalPacket(internal.getRequestId(), responseForward);

                    Messenger.safeRun(ctx, c -> sendPacket(c, responsePacket));
                } catch (Throwable relayError) {
                    this.logger.push("response-relay", AnsiColor.RED_BRIGHT)
                            .error("Failed relaying response " + internal.requestCode(), relayError)
                            .pop();
                }
            });

            backend.forward(internal);
            sendPacket(backendCtx, internal).whenComplete((v, t) -> {
                packet.release();
                backend.decrementInFlight();
                if (t != null) {
                    // Fail fast instead of waiting for timeout when selected node context is stale.
                    this.transactionManager.failRequest(internal.getRequestId(), t);
                    this.backendPool.remove(backend);
                    this.logger.push("queue", AnsiColor.RED_BRIGHT)
                            .warn("Send to backend failed, removed stale node " + backend.typeName()
                                    + " request=" + internal.requestCode() + " error=" + t.getClass().getSimpleName())
                            .pop();
                    this.drainPending();
                }
            });
        }
    }

    protected Packet[] handleResponses(ChannelHandlerContext ctx, InternalPacket internal, ForwardData response) {
        Packet[] responses = response.decode(this.constructor);
        Arrays.stream(responses).forEach(Packet::rewind);
        return responses;
    }

    public void complete(InternalPacket internal, ChannelHandlerContext ctx) {
        if (!this.transactionManager.completeResponse(internal, ctx)) {
            final ForwardData response = internal.getResponse();
            this.logger.push("expired", AnsiColor.RED_BRIGHT)
                    .warn(internal.requestCode(), "from", response.senderId(), "to", response.receiverId()).pop();
        }
    }

    @Override
    public void stop() {
        PacketContext queued;
        while ((queued = this.pendingPackets.pollFirst()) != null) {
            queued.packet().release();
        }
        if (this.ingressDisruptor != null) {
            this.ingressDisruptor.shutdown();
        }
        super.stop();
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
            this.logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        final Channel channel = ctx.channel();
        this.logger.push("connect", AnsiColor.GREEN_BRIGHT).info(Messenger.address(channel)).pop();
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
        this.logger.push("disconnect", AnsiColor.RED_BRIGHT).info(Messenger.address(ctx.channel())).pop();
        if (node != null) {
            node.disconnect();
        }
        super.removeClient(addr);
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

    public TriFunction<Packet, Integer, Long, byte[]> constructor() {
        return constructor;
    }
}