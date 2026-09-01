package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.messaging.listener.decoder.NettyDecoder;
import dev.sweety.netty.messaging.listener.encoder.NettyEncoder;
import dev.sweety.netty.messaging.listener.watcher.NettyWatcher;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;

/**
 * TCP transport — behaviour is byte-identical to the pre-refactor hardcoded {@code Messenger}: this
 * is a pure move of the {@code ServerBootstrap}/{@code Bootstrap} + {@code NioServerSocketChannel}/
 * {@code NioSocketChannel} + option block + stream-framed codec triple.
 */
public final class TcpTransport implements Transport {

    public static final TcpTransport INSTANCE = new TcpTransport();

    private TcpTransport() {
    }

    @Override
    public AbstractBootstrap<?, ?> newBootstrap(boolean server) {
        return server ? new ServerBootstrap() : new Bootstrap();
    }

    @Override
    public void configure(AbstractBootstrap<?, ?> bootstrap, boolean server, EventLoopGroup boss, EventLoopGroup worker,
                           int soBacklog, WriteBufferWaterMark waterMark, int connectTimeoutMillis,
                           ChannelHandler init, int localPort) {
        if (bootstrap instanceof ServerBootstrap serverBootstrap) {
            serverBootstrap.group(boss, worker)
                    .channel(NativeTransport.serverSocketChannelClass())
                    .option(ChannelOption.SO_BACKLOG, soBacklog)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark)
                    .childHandler(init);
        } else if (bootstrap instanceof Bootstrap clientBootstrap) {
            clientBootstrap.group(worker)
                    .channel(NativeTransport.socketChannelClass())
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark)
                    .handler(init);
            if (localPort > 0) {
                clientBootstrap.localAddress(new InetSocketAddress(localPort));
            }
        }
    }

    @Override
    public void installCodecs(ChannelPipeline pipeline, PacketRegistry registry, Messenger owner, int idleTimeoutSeconds) {
        Messenger.installIdleAndBackpressure(pipeline, idleTimeoutSeconds);
        pipeline.addLast(
                new NettyDecoder(registry, owner),
                new NettyWatcher(owner),
                new NettyEncoder(registry)
        );
    }

    @Override
    public ChannelFuture start(AbstractBootstrap<?, ?> bootstrap, boolean server, String host, int port) {
        if (bootstrap instanceof ServerBootstrap serverBootstrap) {
            return serverBootstrap.bind(port);
        } else if (bootstrap instanceof Bootstrap clientBootstrap) {
            return clientBootstrap.connect(host, port);
        }
        throw new IllegalStateException("[Netty] invalid class: " + bootstrap.getClass());
    }

    @Override
    public boolean connectionOriented() {
        return true;
    }
}
