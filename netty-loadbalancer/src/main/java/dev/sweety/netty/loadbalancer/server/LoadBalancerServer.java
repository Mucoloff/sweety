package dev.sweety.netty.loadbalancer.server;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.core.math.vector.queue.LinkedQueue;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.server.backend.Node;
import dev.sweety.netty.loadbalancer.server.pool.INodePool;
import dev.sweety.netty.loadbalancer.server.packets.PacketQueue;
import dev.sweety.netty.loadbalancer.common.packet.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.awt.*;
import java.util.Arrays;

public class LoadBalancerServer extends Server {

    private final INodePool backendPool;
    private final SimpleLogger logger = new SimpleLogger(LoadBalancerServer.class);
    private final LinkedQueue<PacketQueue> pendingPackets = new LinkedQueue<>();

    private final TransactionManager transactionManager = new TransactionManager(this);

    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    private static final long REQUEST_TIMEOUT_SECONDS = 30L;

    public LoadBalancerServer(String host, int port, INodePool backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);

        this.backendPool = backendPool;
        this.backendPool.pool().forEach(node -> node.setLoadBalancer(this));
        this.backendPool.initialize();

        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this.logger);

        this.logger.push("<init>", AnsiColor.fromColor(new Color(148, 186, 76))).info("LoadBalancerServer started on " + host + ":" + port)
                .info("Waiting for connections...");
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof InternalPacket) return;
        logger.push("receive", AnsiColor.CYAN_BRIGHT).info("packet", packet);
        pendingPackets.enqueue(new PacketQueue(packet.rewind(), ctx));
        drainPending();
        logger.pop();
    }

    private void drainPending() {
        PacketQueue pq;
        while ((pq = pendingPackets.dequeue()) != null) {
            final Packet packet = pq.packet();
            final ChannelHandlerContext ctx = pq.ctx();

            Node backend = backendPool.next(packet, ctx);
            if (backend == null) {
                pendingPackets.enqueueFirst(pq);
                logger.push("queue").warn("No backend available. pending packets = " + pendingPackets.size()).pop();
                break;
            }

            final InternalPacket internal = new InternalPacket(new InternalPacket.Forward(getPacketRegistry()::getPacketId, packet));

            transactionManager.registerRequest(internal, REQUEST_TIMEOUT_SECONDS * 500L).whenComplete(((response, throwable) -> {
                if (throwable != null) {
                    backend.timeout(internal.getRequestId());
                    logger.push("transaction", AnsiColor.RED_BRIGHT).info(AnsiColor.fromColor(((int) internal.getRequestId())), internal.requestCode()).error(throwable).pop();
                    return;
                }

                Packet[] responses = response.decode(constructor);

                Arrays.stream(responses).forEach(Packet::rewind);

                logger.push("transaction", AnsiColor.GREEN_BRIGHT).info(AnsiColor.fromColor(((int) internal.getRequestId())), internal.requestCode()).pop();

                Messenger.safeExecute(ctx, () -> sendPacket(ctx, responses));
            }));

            backend.forward(internal);
        }
    }

    public void complete(InternalPacket internal, ChannelHandlerContext ctx) {
        if (transactionManager.completeResponse(internal, ctx)) logger.push("completed", AnsiColor.GREEN_BRIGHT);
        else logger.push("expired", AnsiColor.RED_BRIGHT);

        logger.info(AnsiColor.fromColor(((int) internal.getRequestId())), internal.requestCode()).pop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect").info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.logger.push("disconnect").info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
