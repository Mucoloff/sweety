package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/** A correspondent address behind a shared UDP channel, wrapped behind the uniform {@link Peer} handle. */
public record UdpPeer(Channel channel, InetSocketAddress remote) implements Peer {

    /**
     * Writes the packet through {@link UdpTransport#packets()}'s {@code AddressedPacket} codec.
     * {@link UdpTransport#raw()} consumers (custom wire format) never go through this — they write
     * their own encoded {@code DatagramPacket} directly via {@link #channel()}.
     */
    @Override
    public <T> CompletableFuture<T> send(Packet packet) {
        CompletableFuture<T> future = new CompletableFuture<>();
        channel.writeAndFlush(new AddressedPacket(packet, remote)).addListener(f -> {
            if (f.isSuccess()) future.complete(null);
            else future.completeExceptionally(f.cause());
        });
        return future;
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public SocketAddress remoteAddress() {
        return remote;
    }

    @Override
    public void close() {
        // Shared datagram channel — a single Peer never owns the channel's lifecycle.
    }
}
