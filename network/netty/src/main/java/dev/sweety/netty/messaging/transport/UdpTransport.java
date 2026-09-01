package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.messaging.listener.decoder.DatagramPacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.DatagramPacketEncoder;
import dev.sweety.netty.messaging.listener.watcher.NettyWatcher;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;

/**
 * UDP datagram transport for high-throughput, low-latency communication (gaming, telemetry, presence).
 * Uses {@link DatagramPacketDecoder} and {@link DatagramPacketEncoder} paired symmetrically with {@link AddressedPacket}.
 */
public final class UdpTransport implements Transport {

    private final boolean unconnected;
    private volatile int localPort = -1;

    private UdpTransport(boolean unconnected) {
        this.unconnected = unconnected;
    }

    public static UdpTransport packets() {
        return new UdpTransport(false);
    }

    public static UdpTransport unconnected() {
        return new UdpTransport(true);
    }

    @Override
    public AbstractBootstrap<?, ?> newBootstrap(boolean server) {
        return new Bootstrap();
    }

    @Override
    public void configure(AbstractBootstrap<?, ?> bootstrap, boolean server, EventLoopGroup boss, EventLoopGroup worker,
                           int soBacklog, WriteBufferWaterMark waterMark, int connectTimeoutMillis,
                           ChannelHandler init, int localPort) {
        this.localPort = localPort;
        if (bootstrap instanceof Bootstrap datagramBootstrap) {
            datagramBootstrap.group(worker)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .handler(init);
        }
    }

    @Override
    public void installCodecs(ChannelPipeline pipeline, PacketRegistry registry, Messenger owner, int idleTimeoutSeconds) {
        pipeline.addLast(
                new DatagramPacketDecoder(registry),
                new NettyWatcher(owner),
                new DatagramPacketEncoder(registry)
        );
    }

    @Override
    public ChannelFuture start(AbstractBootstrap<?, ?> bootstrap, boolean server, String host, int port) {
        Bootstrap datagramBootstrap = (Bootstrap) bootstrap;
        if (server) {
            return datagramBootstrap.bind(new InetSocketAddress(host, port));
        } else if (unconnected) {
            return datagramBootstrap.bind(new InetSocketAddress(localPort > 0 ? localPort : 0));
        } else {
            return datagramBootstrap.connect(host, port);
        }
    }

    @Override
    public boolean connectionOriented() {
        return false;
    }
}
