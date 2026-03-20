package dev.sweety.netty.messaging.model;

import dev.sweety.netty.messaging.listener.decoder.NettyDecoder;
import dev.sweety.netty.messaging.listener.encoder.NettyEncoder;
import dev.sweety.netty.messaging.listener.watcher.NettyWatcher;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.time.TimeMode;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.Setter;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.EventExecutor;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

@RecordData(setterTypes = Setter.Type.BUILDER_FLUENT)
public abstract class Messenger<B extends AbstractBootstrap<B, ? extends Channel>> {

    // ==============================/
    // Handlers are now created per-connection instead of shared
    // ==============================/
    @DataIgnore
    private final B bootstrap;
    @DataIgnore
    private final NioEventLoopGroup boss;
    @DataIgnore
    private final NioEventLoopGroup worker;
    // ===================================/
    public static final int SEED = 0x000FFFFF;

    @DataIgnore
    protected Channel channel;

    protected int port;
    protected String host;

    @DataIgnore
    private final AtomicBoolean running = new AtomicBoolean();

    public static TimeMode timeMode = TimeMode.MILLIS;

    private final IPacketRegistry packetRegistry;

    public Messenger(B bootstrap, String host, int port, IPacketRegistry packetRegistry, int localPort) {
        this(bootstrap, host, port, packetRegistry, localPort, null);
    }

    public Messenger(B bootstrap, String host, int port, IPacketRegistry packetRegistry, int localPort, @Nullable Function<SocketChannel, SslHandler> sslProvider) {
        this.bootstrap = bootstrap;
        this.boss = new NioEventLoopGroup();
        this.worker = new NioEventLoopGroup(16);

        this.port = port;
        this.host = host;
        this.packetRegistry = packetRegistry;

        final ChannelInitializer<SocketChannel> init = new ChannelInitializer<>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline p = ch.pipeline();

                if (sslProvider != null) {
                    SslHandler sslHandler = sslProvider.apply(ch);
                    if (sslHandler != null) p.addFirst("ssl", sslHandler);
                }

                p.addLast(
                        new NettyDecoder(Messenger.this.packetRegistry, Messenger.this),
                        new NettyWatcher(Messenger.this),
                        new NettyEncoder(Messenger.this.packetRegistry)
                );
            }
        };

        // Configura bootstrap UNA volta sola
        if (bootstrap instanceof ServerBootstrap server) {
            server.group(this.boss, this.worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(init);
        } else if (bootstrap instanceof Bootstrap client) {
            client.group(this.worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .localAddress(localPort > 0 ? new InetSocketAddress(localPort) : null)
                    .handler(init);
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
            if (f.isSuccess()) {
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
            future.completeExceptionally(
                    new IllegalStateException("[Netty] invalid class: " + this.bootstrap.getClass()));
        }

        return future;
    }

    public <T> CompletableFuture<T> sendPacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<T> future = writePacket(ctx, packet);
        flush(ctx);
        return future;
    }

    public <T> CompletableFuture<T> sendPacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return sendPacket(ctx, msgs[0]);
        CompletableFuture<T> future = writePacket(ctx, msgs);
        flush(ctx);
        return future;
    }

    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet packet) {
        CompletableFuture<T> future = new CompletableFuture<>();
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

    public <T> CompletableFuture<T> writePacket(ChannelHandlerContext ctx, Packet... msgs) {
        if (msgs == null || msgs.length == 0) return CompletableFuture.completedFuture(null);
        if (msgs.length == 1) return writePacket(ctx, msgs[0]);

        CompletableFuture<T> lastWrite = null;
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

    public static <T> CompletableFuture<T> safeExecute(ChannelHandlerContext ctx, Function<ChannelHandlerContext, CompletableFuture<T>> function) {
        //noinspection resource
        final EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) return function.apply(ctx);

        final CompletableFuture<T> future = new CompletableFuture<>();

        executor.execute(() -> {
            final CompletableFuture<T> internal = function.apply(ctx);
            if (internal == null) {
                future.complete(null);
                return;
            }
            internal.whenComplete((v, t) -> {
                if (t != null) future.completeExceptionally(t);
                else future.complete(v);
            });
        });

        return future;
    }

    public static void safeRun(ChannelHandlerContext ctx, Consumer<ChannelHandlerContext> action) {
        //noinspection resource
        final EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) action.accept(ctx);
        else executor.execute(() -> action.accept(ctx));
    }

    public static <M extends Messenger<?>> void init(M messenger) {
        final CountDownLatch latch = new CountDownLatch(1);

        messenger.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                messenger.stop();
            } finally {
                latch.countDown();
            }
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String address(Channel channel) {
        return "remote[%s] local[%s]".formatted(channel.remoteAddress(), channel.localAddress());
    }


    public void stop() {
        this.boss.shutdownGracefully();
        this.worker.shutdownGracefully();
    }

    public abstract void onPacketReceive(ChannelHandlerContext ctx, Packet packet);

    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
    }

    public abstract void exception(ChannelHandlerContext ctx, Throwable throwable);

    public abstract void join(ChannelHandlerContext ctx, ChannelPromise promise);

    public abstract void quit(ChannelHandlerContext ctx, ChannelPromise promise);

}
