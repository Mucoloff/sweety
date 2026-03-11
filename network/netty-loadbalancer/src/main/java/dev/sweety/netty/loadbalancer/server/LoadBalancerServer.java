package dev.sweety.netty.loadbalancer.server;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.core.math.vector.list.BlockingDeque;
import dev.sweety.thread.ProfileThread;
import dev.sweety.thread.ThreadManager;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.common.packet.Packer;
import dev.sweety.netty.loadbalancer.common.packet.internal.ForwardData;
import dev.sweety.netty.loadbalancer.common.packet.internal.InternalPacket;
import dev.sweety.netty.loadbalancer.common.packet.queue.OrderedResponseQueue;
import dev.sweety.netty.loadbalancer.common.packet.queue.PacketContext;
import dev.sweety.netty.loadbalancer.common.packet.queue.PacketReorder;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.pool.IDynamicBackendNodePool;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordGetter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.awt.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

@RecordGetter
public class LoadBalancerServer<Node extends BackendNode> extends Server {

    protected final SimpleLogger logger = new SimpleLogger(LoadBalancerServer.class);

    protected final IDynamicBackendNodePool<Node> backendPool;

    protected final ThreadManager queueScheduler = new ThreadManager("queue-scheduler");
    protected final BlockingDeque<PacketContext> pendingPackets = new BlockingDeque<>();

    protected final TransactionManager transactionManager = new TransactionManager(this);
    protected final PacketReorder reorder = new PacketReorder();

    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public <T extends IDynamicBackendNodePool<Node>> LoadBalancerServer(String host, int port, T backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.backendPool = backendPool;
        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this.logger);

        this.logger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76)))
                .info("LoadBalancerServer started on " + host + ":" + port)
                .info("Waiting for connections...").pop();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        final Node node = this.backendPool.get(ctx);
        if (node != null) node.onPacketReceive(ctx, packet);

        if (!(packet instanceof InternalPacket)) {
            if (node != null && node.handled(packet)) return;
            logger.info("(non-internal), Received", packet.name(), "from " + Messenger.address(ctx.channel()) + " (node:" + (node == null ? null : node.typeName()) + ")");
            return;
        }

        final OrderedResponseQueue queue = this.reorder.enqueue(ctx, this::sendPacket);
        long sequenceId = queue.nextSequenceId();

        this.pendingPackets.enqueue(new PacketContext(packet.retain().rewind(), ctx, sequenceId));
        drainPending();
    }

    @DataIgnore
    private volatile boolean useThreadManager = false;

    private static final long REQUEST_TIMEOUT_SECONDS = 20L;

    public static long requestTimeout() {
        return REQUEST_TIMEOUT_SECONDS * 1000L;
    }

    public void useThreadManager() {
        this.useThreadManager = true;
    }

    public void drainPending() {
        if (this.useThreadManager) {
            final ProfileThread profileThread = this.queueScheduler.getAvailableProfileThread();
            profileThread.execute(this::drainPendingInternal);
            profileThread.decrement();
        } else this.drainPendingInternal();
    }

    public Node next(InternalPacket packet, ChannelHandlerContext ctx) {
        return this.backendPool.next(packet, ctx);
    }

    private synchronized void drainPendingInternal() {
        if (this.pendingPackets.isEmpty()) return;
        PacketContext pq;

        while ((pq = this.pendingPackets.dequeue()) != null) {
            final Packet packet = pq.packet();
            final ChannelHandlerContext ctx = pq.ctx();
            final long sequenceId = pq.sequenceId();

            if (!(packet instanceof InternalPacket internal)) continue;
            if (!internal.hasRequest() && !internal.hasResponse()) continue;

            final Node backend = this.next(internal, ctx);

            if (backend == null) {
                this.pendingPackets.push(pq);
                this.logger.push("queue", AnsiColor.RED_BRIGHT).warn("No backend available. pending packets = " + this.pendingPackets.size()).pop();
                break;
            }

            if (internal.hasResponse()) {
                complete(internal, ctx);
                continue;
            }

            final OrderedResponseQueue responseQueue = this.reorder.find(ctx.channel().remoteAddress());

            this.transactionManager.registerRequest(internal, requestTimeout()).whenComplete((response, throwable) -> {
                backend.onPacketReceive(ctx, internal);
                if (throwable != null) {
                    backend.timeout(internal.getRequestId());
                    this.logger.push("transaction", AnsiColor.RED_BRIGHT).error(internal.requestCode(), throwable.getMessage()).pop();

                    if (responseQueue != null && sequenceId >= 0) responseQueue.complete(sequenceId, Packer.EMPTY);
                    return;
                }

                final Packet[] responses = handleResponses(ctx, internal, response);

                ForwardData request = internal.getRequest();
                ForwardData responseForward = new ForwardData(
                        request.receiverId(),
                        request.senderId(),
                        packetRegistry()::getPacketId,
                        responses
                );
                InternalPacket responsePacket = new InternalPacket(internal.getRequestId(), responseForward);

                if (responseQueue != null && sequenceId >= 0)
                    responseQueue.complete(sequenceId, new Packet[]{responsePacket});
                else Messenger.safeRun(ctx, c -> sendPacket(c, responsePacket));
            });

            ChannelHandlerContext backendCtx = this.backendPool.context(backend);

            if (backendCtx != null) {
                backend.forward(internal);
                sendPacket(backendCtx, internal).whenComplete((v, t) -> backend.decrementInFlight());
            }
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
            this.logger.push("expired", AnsiColor.RED_BRIGHT).warn(internal.requestCode(), "from", response.senderId(), "to", response.receiverId()).pop();
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.transactionManager.shutdown();
        this.reorder.shutdown();
        this.queueScheduler.shutdown();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (!AutoReconnect.exception(throwable)) this.logger.push("exception").error(throwable).pop();
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
        this.logger.push("disconnect", AnsiColor.RED_BRIGHT).info(Messenger.address(ctx.channel()));
        if (node != null) {
            node.disconnect();
        }
        super.removeClient(addr);
        promise.setSuccess();
    }

}
