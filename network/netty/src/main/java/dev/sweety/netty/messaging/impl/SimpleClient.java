package dev.sweety.netty.messaging.impl;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.math.function.TriConsumer;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public abstract class SimpleClient extends Client {

    protected final SimpleLogger logger = new SimpleLogger(getClass());
    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    public SimpleClient(String host, int port, IPacketRegistry packetRegistry) {
        this(host,port,packetRegistry, -1);
    }

    public SimpleClient(String host, int port, IPacketRegistry packetRegistry, int localPort) {
        super(host, port, packetRegistry, localPort);
    }

    public void onConnect(BiConsumer<Channel, ChannelHandlerContext> onConnect) {
        onConnect(c -> {
            ChannelHandlerContext ctx = c.pipeline().firstContext();
            Messenger.safeRun(ctx, _ctx -> onConnect.accept(c, _ctx));
        });
    }

    @Override
    public CompletableFuture<Channel> connect() {
        return super.connect().exceptionally((t) -> {
            this.autoReconnect.onException(t);
            return null;
        }).whenComplete((c, t) -> {
            if (c != null) this.autoReconnect.complete();
        });
    }

    @Override
    public void stop() {
        autoReconnect.shutdown();
        super.stop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        logger.error("Errore nel client: ", throwable);
        autoReconnect.onException(throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("connect", AnsiColor.GREEN_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.push("disconnect", AnsiColor.RED_BRIGHT).info(ctx.channel().remoteAddress()).pop();
        promise.setSuccess();
        autoReconnect.onQuit();
    }

}
