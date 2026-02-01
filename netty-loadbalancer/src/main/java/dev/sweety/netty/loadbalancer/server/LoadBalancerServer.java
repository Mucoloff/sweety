package dev.sweety.netty.loadbalancer.server;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.core.math.vector.deque.BlockingDeque;
import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.thread.ThreadManager;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.common.packet.InternalPacket;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.packets.PacketContext;
import dev.sweety.netty.loadbalancer.server.pool.IBackendNodePool;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.awt.*;
import java.util.Arrays;

public class LoadBalancerServer extends Server {

    private final IBackendNodePool backendPool;
    private final SimpleLogger logger = new SimpleLogger(LoadBalancerServer.class);

    private final BlockingDeque<PacketContext> pendingPackets = new BlockingDeque<>();

    private final TransactionManager transactionManager = new TransactionManager(this);

    private final ThreadManager queueScheduler = new ThreadManager("queue-scheduler");

    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public LoadBalancerServer(String host, int port, IBackendNodePool backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);

        this.backendPool = backendPool;
        this.backendPool.pool().forEach(node -> node.setLoadBalancer(this));
        this.backendPool.initialize();

        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this.logger);

        this.logger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76))).info("LoadBalancerServer started on " + host + ":" + port)
                .info("Waiting for connections...").pop();

    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof InternalPacket) return;
        pendingPackets.enqueue(new PacketContext(packet.retain().rewind(), ctx));
        drainPending();
    }

    //todo make a settings class for these
    private volatile boolean useThreadManager = true;
    private static final long REQUEST_TIMEOUT_SECONDS = 10L;

    public void useThreadManager() {
        this.useThreadManager = true;
    }

    public void drainPending() {
        if (useThreadManager) {
            final ProfileThread profileThread = this.queueScheduler.getAvailableProfileThread();
            profileThread.execute(this::drainPendingInternal);
            profileThread.decrement();
        } else this.drainPendingInternal();
    }

    private void drainPendingInternal() {
        if (pendingPackets.isEmpty()) return;
        PacketContext pq;

        while ((pq = pendingPackets.dequeue()) != null) {
            final Packet packet = pq.packet();
            final ChannelHandlerContext ctx = pq.ctx();

            BackendNode backend = backendPool.next(packet, ctx);
            if (backend == null) {
                pendingPackets.push(pq);
                logger.push("queue", AnsiColor.RED_BRIGHT).warn("No backend available. pending packets = " + pendingPackets.size()).pop();
                break;
            }

            final InternalPacket internal = new InternalPacket(new InternalPacket.Forward(getPacketRegistry()::getPacketId, packet));

            transactionManager.registerRequest(internal, REQUEST_TIMEOUT_SECONDS * 1000L).whenComplete(((response, throwable) -> {
                if (throwable != null) {
                    backend.timeout(internal.getRequestId());
                    logger.push("transaction", AnsiColor.RED_BRIGHT).error(internal.requestCode(), throwable).pop();
                    return;
                }

                Packet[] responses = response.decode(constructor);

                Arrays.stream(responses).forEach(Packet::rewind);

                Messenger.safeExecute(ctx, c -> sendPacket(c, responses));
            }));

            backend.forward(ctx, internal);
        }
    }

    @Override
    public void stop() {
        super.stop();
        transactionManager.shutdown();
        queueScheduler.shutdown();
        backendPool.pool().forEach(BackendNode::stop);
    }

    public void complete(InternalPacket internal, ChannelHandlerContext ctx) {
        if (!transactionManager.completeResponse(internal, ctx)) {
            logger.push("expired", AnsiColor.RED_BRIGHT).warn(internal.requestCode()).pop();
        }
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (!AutoReconnect.exception(throwable)) logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect", AnsiColor.GREEN_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect", AnsiColor.RED_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
