package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.messaging.listener.decoder.DatagramPacketDecoder;
import dev.sweety.netty.messaging.listener.decoder.RawDatagramPacketDecoder;
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
 * UDP transport: a datagram "server" binds through a plain {@link Bootstrap} (no {@code ServerBootstrap}
 * — datagram channels have no accept/child-channel split), same as a datagram "client" that connects.
 *
 * <p>Two codec modes: {@link #packets()} installs a generic {@link dev.sweety.netty.packet.model.Packet}
 * codec pair over datagrams; {@link #raw()} wraps every inbound datagram in a {@link RawDatagramPacket}
 * and delivers it through the exact same {@code Messenger#onPacketReceive} path TCP already uses (via
 * {@link RawDatagramPacketDecoder} + {@link NettyWatcher} — no separate hook), required whenever the
 * wire format cannot be a generic packet codec (e.g. a custom type-byte + encrypted-blob envelope that
 * only becomes a {@code Packet} after application-level decryption).
 */
public final class UdpTransport implements Transport {

    private final boolean rawMode;
    private final boolean unconnected;
    private volatile int localPort = -1;

    private UdpTransport(boolean rawMode, boolean unconnected) {
        this.rawMode = rawMode;
        this.unconnected = unconnected;
    }

    public static UdpTransport packets() {
        return new UdpTransport(false, false);
    }

    public static UdpTransport raw() {
        return new UdpTransport(true, false);
    }

    /**
     * A raw-mode client that {@code bind()}s an ephemeral local socket instead of {@code connect()}ing
     * to a fixed remote — required to send/receive datagrams addressed to more than one remote peer on
     * the same socket (P2P direct path). Loses the kernel-level source-address filtering a connected
     * socket gives for free; callers MUST validate {@code RawDatagramPacket#sender()} themselves.
     */
    public static UdpTransport rawUnconnected() {
        return new UdpTransport(true, true);
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
        if (rawMode) {
            pipeline.addLast(new RawDatagramPacketDecoder(), new NettyWatcher(owner));
        } else {
            pipeline.addLast(
                    new DatagramPacketDecoder(registry),
                    new NettyWatcher(owner),
                    new DatagramPacketEncoder(registry)
            );
        }
    }

    @Override
    public ChannelFuture start(AbstractBootstrap<?, ?> bootstrap, boolean server, String host, int port) {
        Bootstrap datagramBootstrap = (Bootstrap) bootstrap;
        // A datagram "server" binds a shared socket; an unconnected client binds an ephemeral (or
        // pinned) local socket so it can send/receive to more than one remote address (P2P direct
        // path); an ordinary datagram "client" that only ever talks to one remote still `connect()`s
        // (fixes the peer address, matching UdpSocialClient's server-relay usage).
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
