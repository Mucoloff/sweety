package dev.sweety.netty.messaging;

import dev.sweety.math.registry.LongKeyedRegistry;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.messaging.transport.Transport;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;


public abstract class Server extends Messenger {

    // Netty's ChannelId isn't a single long (machine-id/pid/sequence/random/timestamp composite), so a
    // cheap numeric identity has to be assigned ourselves rather than derived per-lookup from
    // Channel.id().asLongText() (a string alloc + string-keyed hash on every access). Assigned once at
    // addClient(), read back via this attribute wherever the channel needs its own id again.
    private static final AttributeKey<Long> CONNECTION_ID = AttributeKey.valueOf("luce.connectionId");
    private static final AtomicLong NEXT_CONNECTION_ID = new AtomicLong();

    private final LongKeyedRegistry<ChannelHandlerContext> clients = new LongKeyedRegistry<>();

    public Server(String host, int port, PacketRegistry packetRegistry) {
        super(true, host, port, packetRegistry, -1);
    }

    /** For a non-TCP transport (e.g. {@code UdpTransport.raw()}/{@code packets()}) — see {@code SimpleServer}'s overload. */
    protected Server(Transport transport, String host, int port, PacketRegistry packetRegistry) {
        super(transport, true, host, port, packetRegistry, -1);
    }

    public void broadcastPacket(final Packet msg) {
        if (clients.isEmpty()) return;
        clients.values().forEach((ctx) -> sendPacket(ctx, msg));
    }

    public void broadcastPacket(final Packet... msgs) {
        if (clients.isEmpty()) return;
        clients.values().forEach((ctx) -> sendPacket(ctx, msgs));
    }

    /** {@code address} is kept only for callers' logging; the client is now keyed by an assigned connectionId. */
    public void addClient(ChannelHandlerContext ctx, SocketAddress address) {
        long id = NEXT_CONNECTION_ID.getAndIncrement();
        ctx.channel().attr(CONNECTION_ID).set(id);
        clients.put(id, ctx);
        ctx.channel().closeFuture().addListener(f -> clients.remove(id));
    }

    /** The connectionId assigned to this channel by {@link #addClient}, or {@code null} if never registered. */
    public static Long connectionIdOf(ChannelHandlerContext ctx) {
        return ctx.channel().attr(CONNECTION_ID).get();
    }

    public Collection<ChannelHandlerContext> clients() {
        return clients.values();
    }

    /** O(1) lookup by the connectionId assigned in {@link #addClient}, or {@code null} if not live. */
    public ChannelHandlerContext client(long connectionId) {
        return clients.get(connectionId);
    }
}