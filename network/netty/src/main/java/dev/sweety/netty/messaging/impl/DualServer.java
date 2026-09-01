package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * High-performance Dual-Transport (TCP + UDP) server running on a single shared EventLoopGroup.
 */
public abstract class DualServer extends Server {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DualServer.class);
    private final dev.sweety.netty.messaging.transport.EndpointRegistry endpointRegistry = new dev.sweety.netty.messaging.transport.EndpointRegistry();

    public DualServer(String host, int port, PacketRegistry packetRegistry) {
        super(TransportMode.DUAL, host, port, packetRegistry);
    }

    public dev.sweety.netty.messaging.transport.EndpointRegistry endpointRegistry() {
        return endpointRegistry;
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (ctx.channel().remoteAddress() != null) {
            addClient(ctx, ctx.channel().remoteAddress());
        }
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        promise.setSuccess();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        LOGGER.error("DualServer exception: ", throwable);
    }
}
