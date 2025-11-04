package dev.sweety.network.cloud.messaging.listener.watcher;

import dev.sweety.network.cloud.messaging.model.Messenger;
import dev.sweety.network.cloud.packet.model.Packet;
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
        if (msg instanceof Packet packet) this.messenger.onPacketReceive(ctx, packet);
        else ctx.close();
    }

    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.messenger.join(ctx, ctx.newPromise());

        try {
            for (Packet packet : this.messenger.getPackets()) ctx.channel().write(packet);
            ctx.channel().flush();
        } catch (Exception e) {
            this.messenger.exception(ctx, new Exception("Watcher failed to send initial packets", e));
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
