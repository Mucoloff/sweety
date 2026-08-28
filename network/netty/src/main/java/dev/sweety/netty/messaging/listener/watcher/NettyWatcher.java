package dev.sweety.netty.messaging.listener.watcher;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Sharable
public class NettyWatcher extends ChannelInboundHandlerAdapter {
    private static final SimpleLogger LOGGER = SimpleLogger.of("netty-watcher");
    protected final Messenger messenger;

    public NettyWatcher(Messenger messenger) {
        this.messenger = messenger;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet packet) {
            //anche questo log consuma risorse
            //SimpleLogger.log(LogLevel.INFO, "netty-watcher", "dispatch packet " + packet.name() + " from " + ctx.channel().remoteAddress());
            this.messenger.onPacketReceive(ctx, packet);
        } else {
            LOGGER.warn("non-packet msg class={} from {}", msg != null ? msg.getClass().getName() : "null", ctx.channel().remoteAddress());
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
