package dev.sweety.netty.messaging.impl;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public abstract class SimpleServer extends Server {

    protected final SimpleLogger logger = new SimpleLogger(getClass());

    public SimpleServer(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Exception: ", throwable);
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
