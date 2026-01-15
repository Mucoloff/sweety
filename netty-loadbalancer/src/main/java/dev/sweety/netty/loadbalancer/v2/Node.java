package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Node extends Client {

    @Setter
    private LBServer loadBalancer;
    private final SimpleLogger logger = new SimpleLogger(Node.class);

    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    public Node(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.logger.push("" + port);
    }

    @Override
    public CompletableFuture<Channel> connect() {
        return super.connect().exceptionally(t -> {
            this.autoReconnect.onException(t);
            return null;
        });
    }

    @Override
    public void stop() {
        this.autoReconnect.shutdown();
        super.stop();
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive", AnsiColor.YELLOW_BRIGHT).info("packet", packet);
        if (loadBalancer != null && packet instanceof InternalPacket internal) {
            logger.push(internal.requestCode()).info("forwarding to load balancer").pop();
            loadBalancer.complete(internal);
        }

        logger.pop();
    }

    public void forward(InternalPacket internal) {
        logger.push("forward" + internal.requestCode(), AnsiColor.PURPLE_BRIGHT).info("packet:", internal).pop();
        sendPacket(internal);
    }

    public boolean isActive() {
        return this.channel != null && this.channel.isActive() && this.running();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.push("exception").error(throwable).pop();
        autoReconnect.onException(throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect").info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect").info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
        autoReconnect.onQuit();
    }

}
