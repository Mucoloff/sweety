package dev.sweety.netty.messaging.impl;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.transport.Transport;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public abstract class SimpleServer extends Server {

    protected final SimpleLogger logger = SimpleLogger.of(getClass());

    public SimpleServer(String host, int port, PacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    /** For a non-TCP transport (e.g. {@code UdpTransport.raw()}) — see {@code Server}'s overload. */
    protected SimpleServer(Transport transport, String host, int port, PacketRegistry packetRegistry) {
        super(transport, host, port, packetRegistry);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Exception: ", throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.profile("connect").info(ctx.channel().remoteAddress());
        // A shared datagram channel has no per-connection accept() — addClient's connectionId
        // registry only makes sense for a real per-connection stream channel (TCP).
        if (connectionOriented()) super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.profile("disconnect").info(ctx.channel().remoteAddress());
        // Cleanup already happens via the channel's closeFuture listener added in addClient().
        promise.setSuccess();
    }

}
