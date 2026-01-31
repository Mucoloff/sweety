package dev.sweety.netty.messaging.listener.watcher;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

@Sharable
public class NettyWatcher extends ChannelHandlerAdapter {
    protected final Messenger<?> messenger;

    public NettyWatcher(Messenger<?> messenger) {
        this.messenger = messenger;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet packet) {
            try {
                this.messenger.onPacketReceive(ctx, packet);
            } finally {
                packet.release();
            }
        } else ctx.fireChannelRead(msg);
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.messenger.join(ctx, ctx.newPromise());

        final Packet[] initial = this.messenger.getPackets();
        if (initial != null && initial.length > 0) {
            this.messenger.sendPacket(ctx, initial).exceptionally(ex -> {
                this.messenger.exception(ctx, new Exception("Watcher failed to send initial packets", ex));
                return null;
            });
        }

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
