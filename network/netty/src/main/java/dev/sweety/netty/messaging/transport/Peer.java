package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.packet.model.Packet;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * Uniform remote-endpoint handle across transports: a live TCP connection ({@link TcpPeer}) or a
 * UDP correspondent address behind a shared datagram channel ({@link UdpPeer}). Every existing
 * {@code ChannelHandlerContext}-based overload on {@code Messenger}/{@code Server} stays as-is —
 * this is an additive surface, not a replacement, so the 13 pre-existing subclasses compile untouched.
 */
public sealed interface Peer permits TcpPeer, UdpPeer {

    <T> CompletableFuture<T> send(Packet packet);

    boolean isActive();

    SocketAddress remoteAddress();

    void close();
}
