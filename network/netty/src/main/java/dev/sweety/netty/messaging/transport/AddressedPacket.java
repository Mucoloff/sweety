package dev.sweety.netty.messaging.transport;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.math.pool.Acquire;
import dev.sweety.math.pool.ObjectPool;
import dev.sweety.math.pool.Pooled;
import dev.sweety.math.pool.Release;
import dev.sweety.netty.packet.model.Packet;

import java.net.InetSocketAddress;

/**
 * An addressed datagram packet for UDP transport: pairs a {@link Packet} payload with an {@link InetSocketAddress}
 * (either recipient for outbound writes or sender for inbound reads).
 * Backed by thread-local object pooling to eliminate GC pressure under high packet rates.
 */
@Pooled(pool = AddressedPacket.AddressedPacketPool.class)
public final class AddressedPacket extends Packet {

    private Packet packet;
    private InetSocketAddress recipient;

    public AddressedPacket() {}

    public AddressedPacket(Packet packet, InetSocketAddress recipient) {
        this.packet = packet;
        this.recipient = recipient;
    }

    public static final class AddressedPacketPool {
        public static final ObjectPool<AddressedPacket> POOL =
                ObjectPool.threadLocal(AddressedPacket::new).reset(AddressedPacket::reset).build();
    }

    @Acquire
    public static AddressedPacket acquire(Packet packet, InetSocketAddress recipient) {
        AddressedPacket addressed = AddressedPacketPool.POOL.acquire();
        addressed.packet = packet;
        addressed.recipient = recipient;
        return addressed;
    }

    @Release
    public void release() {
        AddressedPacketPool.POOL.release(this);
    }

    public void reset() {
        this.packet = null;
        this.recipient = null;
    }

    public Packet packet() {
        return packet;
    }

    public AddressedPacket packet(Packet packet) {
        this.packet = packet;
        return this;
    }

    /** Outbound destination socket address. */
    public InetSocketAddress recipient() {
        return recipient;
    }

    public AddressedPacket recipient(InetSocketAddress recipient) {
        this.recipient = recipient;
        return this;
    }

    /** Inbound sender socket address (alias for recipient). */
    public InetSocketAddress sender() {
        return recipient;
    }

    @Override
    public void write(BufferWriter buffer) {
        if (packet != null) packet.write(buffer);
    }

    @Override
    public void read(BufferReader buffer) {
        if (packet != null) packet.read(buffer);
    }
}
