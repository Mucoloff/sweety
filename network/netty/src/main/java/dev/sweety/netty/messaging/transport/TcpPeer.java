package dev.sweety.netty.messaging.transport;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;

/** A live TCP connection's {@link ChannelHandlerContext}, wrapped behind the uniform {@link Peer} handle. */
public record TcpPeer(ChannelHandlerContext ctx) implements Peer {

    public static TcpPeer of(ChannelHandlerContext ctx) {
        return new TcpPeer(ctx);
    }

    /**
     * Not wired to a {@link Messenger} instance by design (a {@link Peer} is transport-endpoint
     * identity, not a messenger reference) — callers still send via the existing
     * {@code Messenger#sendPacket(ChannelHandlerContext, Packet)} overload using {@link #ctx()}.
     */
    @Override
    public <T> CompletableFuture<T> send(Packet packet) {
        throw new UnsupportedOperationException("Use Messenger#sendPacket(ctx(), packet) instead");
    }

    @Override
    public boolean isActive() {
        return ctx.channel().isActive();
    }

    @Override
    public SocketAddress remoteAddress() {
        return ctx.channel().remoteAddress();
    }

    @Override
    public void close() {
        ctx.close();
    }
}
