package dev.sweety.netty.messaging.model;

import dev.sweety.netty.messaging.listener.decoder.NettyDecoder;
import dev.sweety.netty.messaging.listener.encoder.NettyEncoder;
import dev.sweety.netty.messaging.listener.watcher.NettyWatcher;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.core.time.TimeMode;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public abstract class Messenger<B extends AbstractBootstrap<B, ? extends Channel>> {

    // ==============================/
    // Handlers are now created per-connection instead of shared
    // ==============================/
    private final B bootstrap;
    private final NioEventLoopGroup boss;
    private final NioEventLoopGroup worker;
    // ===================================/
    public static final int SEED = 0x000FFFFF;

    protected Channel channel;

    @Getter
    @Setter
    protected int port;
    @Getter
    @Setter
    protected String host;
    @Getter
    private final Packet[] packets;
    private final AtomicBoolean running = new AtomicBoolean();

    @Getter
    @Setter
    public static TimeMode timeMode = TimeMode.MILLIS;

    @Getter
    private final IPacketRegistry packetRegistry;

    public Messenger(B bootstrap, String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        this.bootstrap = bootstrap;
        this.boss = new NioEventLoopGroup();
        this.worker = new NioEventLoopGroup(16);

        //
        // Handlers are now created per-connection in initChannel()
        //

        this.port = port;
        this.host = host;
        this.packets = packets;
        this.packetRegistry = packetRegistry;

        final Consumer<SocketChannel> initChannelConsumer = (ch) -> {
            ChannelPipeline p = ch.pipeline();
            p.addLast(
                    new NettyDecoder(this.packetRegistry),
                    new NettyWatcher(this),
                    new NettyEncoder(this.packetRegistry)
            );

        };

        // Configura bootstrap UNA volta sola
        if (bootstrap instanceof ServerBootstrap server) {
            server.group(this.boss, this.worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            initChannelConsumer.accept(ch);
                        }
                    });
        } else if (bootstrap instanceof Bootstrap client) {
            client.group(this.worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            initChannelConsumer.accept(ch);
                        }
                    });
        }
    }

    public Channel start() {
        return this.connect().join();
    }

    @Setter
    private Consumer<Channel> onConnect;

    public CompletableFuture<Channel> connect() {
        final CompletableFuture<Channel> future = new CompletableFuture<>();

        ChannelFutureListener channelFutureListener = (f) -> {
            boolean success = f.isSuccess();
            running(success);
            if (success) {
                future.complete(this.channel = f.channel());
                if (this.onConnect != null) onConnect.accept(this.channel);
            } else {
                future.completeExceptionally(f.cause());
            }
        };

        if (this.bootstrap instanceof ServerBootstrap server) {
            server.bind(this.port).addListener(channelFutureListener);
        } else if (this.bootstrap instanceof Bootstrap client) {
            client.connect(this.host, this.port).addListener(channelFutureListener);
        } else {
            running(false);
            future.completeExceptionally(
                    new IllegalStateException("[Netty] invalid class: " + this.bootstrap.getClass()));
        }

        return future;
    }

    public CompletableFuture<Void> sendPacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<Void> future = writePacket(ctx, packet);
        flush(ctx);
        return future;
    }

    public CompletableFuture<Void> sendPacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return sendPacket(ctx, msgs[0]);
        CompletableFuture<Void> future = writePacket(ctx, msgs);
        flush(ctx);
        return future;
    }

    public CompletableFuture<Void> writePacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (ctx == null || !ctx.channel().isActive()) {
            future.completeExceptionally(new IllegalStateException("Channel not active or context is null"));
            return future;
        }
        onPacketSend(ctx, packet, true);
        ctx.channel().write(packet).addListener(f -> {
            if (f.isSuccess()) {
                onPacketSend(ctx, packet, false);
                future.complete(null);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<Void> writePacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return writePacket(ctx, msgs[0]);

        CompletableFuture<Void> lastWrite = null;
        for (Packet packet : msgs) {
            lastWrite = writePacket(ctx, packet);
        }
        return lastWrite != null ? lastWrite : CompletableFuture.completedFuture(null);
    }

    public void flush(ChannelHandlerContext ctx) {
        if (ctx != null && ctx.channel().isActive()) {
            ctx.channel().flush();
        }
    }

    public static void safeExecute(ChannelHandlerContext ctx, Consumer<ChannelHandlerContext> r) {
        //noinspection resource
        final EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) r.accept(ctx);
        else executor.execute(() -> r.accept(ctx));
    }


    public void stop() {
        running(false);
        this.boss.shutdownGracefully();
        this.worker.shutdownGracefully();
    }

    protected void running(final boolean running) {
        this.running.set(running);
    }

    public boolean running() {
        return this.running.get();
    }

    public abstract void onPacketReceive(ChannelHandlerContext ctx, Packet packet);

    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
    }

    public abstract void exception(ChannelHandlerContext ctx, Throwable throwable);

    public abstract void join(ChannelHandlerContext ctx, ChannelPromise promise);

    public abstract void quit(ChannelHandlerContext ctx, ChannelPromise promise);

}
