package dev.sweety.netty.messaging.listener.watcher;

import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Sharable
public class NettyWatcher extends ChannelInboundHandlerAdapter {
    protected final Messenger<?> messenger;

    public NettyWatcher(Messenger<?> messenger) {
        this.messenger = messenger;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet packet) {
            try {
                //anche questo log consuma risorse
                //SimpleLogger.log(LogLevel.INFO, "netty-watcher", "dispatch packet " + packet.name() + "(" + packet.id() + ") from " + ctx.channel().remoteAddress());
                this.messenger.onPacketReceive(ctx, packet);
            } finally {
                packet.release();
            }
        } else {
            SimpleLogger.log(LogLevel.WARN, "netty-watcher", "non-packet msg class=" + (msg != null ? msg.getClass().getName() : "null") + " from " + ctx.channel().remoteAddress());
            ctx.fireChannelRead(msg);
        }
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.messenger.join(ctx, ctx.newPromise());
        super.channelActive(ctx);
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.messenger.quit(ctx, ctx.newPromise());
        super.channelInactive(ctx);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        this.messenger.exception(ctx, cause);
    }
}
