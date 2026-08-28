package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;

/**
 * Transport strategy owning everything about {@link Messenger} that used to be hardcoded to TCP:
 * which {@link AbstractBootstrap}/{@link Channel} type backs a role, how its options are applied,
 * which codecs sit in the pipeline, and how it is started ({@code bind} vs {@code connect}).
 *
 * <p>{@link #connectionOriented()} gates behaviour that only makes sense for a stream/connection
 * (idle-disconnect, per-channel client registry, auto-reconnect) — meaningless on a single shared
 * datagram channel.
 */
public interface Transport {

    /** A fresh, unconfigured bootstrap appropriate for this transport/role. */
    AbstractBootstrap<?, ?> newBootstrap(boolean server);

    /** Wires groups, channel type, socket options and the shared {@code init} handler onto {@code bootstrap}. */
    void configure(AbstractBootstrap<?, ?> bootstrap, boolean server, EventLoopGroup boss, EventLoopGroup worker,
                   int soBacklog, WriteBufferWaterMark waterMark, int connectTimeoutMillis,
                   ChannelHandler init, int localPort);

    /** Installs this transport's codec/handler chain onto a freshly-initialized channel's pipeline. */
    void installCodecs(ChannelPipeline pipeline, PacketRegistry registry, Messenger owner, int idleTimeoutSeconds);

    /** {@code bind} for a server role, {@code connect} for a client role — replaces the old {@code instanceof} dispatch. */
    ChannelFuture start(AbstractBootstrap<?, ?> bootstrap, boolean server, String host, int port);

    /**
     * {@code true} for a stream transport with per-connection lifecycle (TCP); {@code false} for a
     * connectionless shared-channel transport (UDP), where idle-disconnect, the per-channel client
     * registry and auto-reconnect are all meaningless.
     */
    boolean connectionOriented();
}
