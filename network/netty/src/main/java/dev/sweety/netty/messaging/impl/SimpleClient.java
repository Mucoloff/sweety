package dev.sweety.netty.messaging.impl;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.Client;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.messaging.transport.Transport;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public abstract class SimpleClient extends Client {

    protected final SimpleLogger logger = SimpleLogger.of(getClass());
    private final AutoReconnect autoReconnect = new AutoReconnect(2500L, TimeUnit.MILLISECONDS, this::start);

    public SimpleClient(String host, int port, PacketRegistry packetRegistry) {
        this(host,port,packetRegistry, -1);
    }

    public SimpleClient(String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(host, port, packetRegistry, localPort);
    }

    /** For a non-TCP transport (e.g. {@code UdpTransport.raw()}) — see {@code Client}'s overload. */
    protected SimpleClient(Transport transport, String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(transport, host, port, packetRegistry, localPort);
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
            if (connectionOriented()) this.autoReconnect.onException(t);
            return null;
        }).whenComplete((c, t) -> {
            if (c != null && connectionOriented()) this.autoReconnect.complete();
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
        if (connectionOriented()) autoReconnect.onException(throwable);
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.profile("connect").info(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.profile("disconnect").info(ctx.channel().remoteAddress());
        promise.setSuccess();
        if (connectionOriented()) autoReconnect.onQuit();
    }

}
