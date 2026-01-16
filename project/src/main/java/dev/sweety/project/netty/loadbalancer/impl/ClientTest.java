package dev.sweety.project.netty.loadbalancer.impl;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ClientTest extends Client {

    private final SimpleLogger logger = new SimpleLogger(ClientTest.class);
    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    public ClientTest(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        logger.push("receive").info("packet", packet);

        if (packet instanceof TextPacket text)
            logger.push("text").info("content: " + text.getText()).pop();

        logger.pop();
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
