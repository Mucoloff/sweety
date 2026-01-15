package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.vector.queue.LinkedQueue;
import dev.sweety.netty.feature.TransactionManager;
import dev.sweety.netty.loadbalancer.PacketQueue;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class LBServer extends Server {

    private final NodePool backendPool;
    private final SimpleLogger logger = new SimpleLogger(LBServer.class);
    private final LinkedQueue<PacketQueue> pendingPackets = new LinkedQueue<>();

    private final TransactionManager transactionManager = new TransactionManager(this);

    private final PacketEncoder encoder;
    private final PacketDecoder decoder;

    private static final long REQUEST_TIMEOUT_SECONDS = 30L;

    public LBServer(String host, int port, NodePool backendPool, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);

        this.backendPool = backendPool;
        this.backendPool.pool().forEach(node -> node.setLoadBalancer(this));
        this.backendPool.initialize();

        this.encoder = new PacketEncoder(packetRegistry).noChecksum();
        this.decoder = new PacketDecoder(packetRegistry).noChecksum();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive").info("packet", packet);
        final Node backend = backendPool.nextBackend(packet, ctx);

        if (backend == null) {
            pendingPackets.enqueue(new PacketQueue(packet, ctx));
            logger.warn("No backend available. Packet queued: pending packets = " + pendingPackets.size()).pop();
            return;
        }

        final InternalPacket internal = new InternalPacket(new InternalPacket.Forward(encoder, packet.rewind()));

        transactionManager.registerRequest(internal, REQUEST_TIMEOUT_SECONDS).whenComplete(((forward, throwable) -> {
            if (throwable != null) {
                logger.push("transaction").error(throwable).pop();
                return;
            }

            Packet[] responses = forward.decode(decoder);

            sendPacket(ctx, responses);
        }));

        backend.forward(internal);

        logger.pop();
    }

    public void forward(InternalPacket internal) {
        if (transactionManager.completeResponse(internal))
            logger.push(internal.requestCode()).info("completed transaction").pop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("Join").info(ctx.channel().remoteAddress()).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.logger.push("quit").info(ctx.channel().remoteAddress()).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

}
