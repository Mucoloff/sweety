package dev.sweety.network.cloud.messaging.model;

import dev.sweety.network.cloud.messaging.listener.decoder.NettyDecoder;
import dev.sweety.network.cloud.messaging.listener.encoder.NettyEncoder;
import dev.sweety.network.cloud.messaging.listener.watcher.NettyWatcher;
import dev.sweety.network.cloud.packet.model.Packet;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new NettyDecoder(this.packetRegistry), new NettyWatcher(this), new NettyEncoder(this.packetRegistry));
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

    public CompletableFuture<Channel> connect() {
        final CompletableFuture<Channel> future = new CompletableFuture<>();

        if (this.bootstrap instanceof ServerBootstrap server) {
            server.bind(this.port).addListener((ChannelFutureListener) (bindFuture) -> {
                boolean success = bindFuture.isSuccess();
                running(success);
                if (success) {
                    future.complete(bindFuture.channel());
                } else {
                    future.completeExceptionally(bindFuture.cause());
                }
            });
        } else if (this.bootstrap instanceof Bootstrap client) {
            client.connect(this.host, this.port).addListener((ChannelFutureListener) (connectFuture) -> {
                boolean success = connectFuture.isSuccess();
                running(success);
                if (success) {
                    this.channel = connectFuture.channel();
                    future.complete(this.channel);
                } else {
                    future.completeExceptionally(connectFuture.cause());
                }
            });
        } else {
            running(false);
            future.completeExceptionally(
                    new IllegalStateException("[Netty] invalid class: " + this.bootstrap.getClass()));
        }

        return future;
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

    public abstract void exception(ChannelHandlerContext ctx, Throwable throwable);
    public abstract void join(ChannelHandlerContext ctx, ChannelPromise promise);
    public abstract void quit(ChannelHandlerContext ctx, ChannelPromise promise);

}
