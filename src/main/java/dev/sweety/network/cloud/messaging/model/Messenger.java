package dev.sweety.network.cloud.messaging.model;

import dev.sweety.network.cloud.messaging.listener.decoder.NettyDecoder;
import dev.sweety.network.cloud.messaging.listener.encoder.NettyEncoder;
import dev.sweety.network.cloud.messaging.listener.watcher.NettyWatcher;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Messenger<B extends AbstractBootstrap<B, ? extends Channel>> {

    private final NettyWatcher watcher;
    private final B bootstrap;
    private final NioEventLoopGroup boss;
    private final NioEventLoopGroup worker;
    protected Channel channel;
    protected final int port;
    protected final String host;

    @Getter
    private final PacketOut[] packets;
    private final AtomicBoolean running = new AtomicBoolean();

    public Messenger(B bootstrap, String host, int port, PacketOut[] packets) {
        this.bootstrap = bootstrap;
        this.boss = new NioEventLoopGroup();
        this.worker = new NioEventLoopGroup(16);
        this.watcher = new NettyWatcher(this);
        this.packets = packets;
        this.port = port;
        this.host = host;

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
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new NettyDecoder(), Messenger.this.watcher, new NettyEncoder());
                        }
                    });
        } else if (bootstrap instanceof Bootstrap client) {
            client.group(this.worker)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new NettyDecoder(), Messenger.this.watcher, new NettyEncoder());
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

    public abstract void onPacketReceive(ChannelHandlerContext ctx, PacketIn packet);

    public abstract void exception(ChannelHandlerContext ctx, Throwable throwable);

    public abstract void join(ChannelHandlerContext ctx, ChannelPromise promise);

    public abstract void quit(ChannelHandlerContext ctx, ChannelPromise promise);

    protected void running(final boolean running) {
        this.running.set(running);
    }

    public boolean running() {
        return this.running.get();
    }
}
