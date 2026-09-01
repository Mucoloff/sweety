package dev.sweety.loadbalancer.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.annotation.TransportHint;
import dev.sweety.netty.packet.model.Packet;

@TransportHint(TransportMode.UDP)
public final class PongPacket extends Packet {

    private long clientTimestampNanos;
    private long serverTimestampNanos;

    public PongPacket() {}

    public PongPacket(long clientTimestampNanos, long serverTimestampNanos) {
        this.clientTimestampNanos = clientTimestampNanos;
        this.serverTimestampNanos = serverTimestampNanos;
    }

    public static PongPacket of(long clientTimestampNanos, long serverTimestampNanos) {
        return new PongPacket(clientTimestampNanos, serverTimestampNanos);
    }

    public long clientTimestampNanos() {
        return clientTimestampNanos;
    }

    public long serverTimestampNanos() {
        return serverTimestampNanos;
    }

    public long roundTripLatencyNanos() {
        return System.nanoTime() - clientTimestampNanos;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeLong(clientTimestampNanos);
        buffer.writeLong(serverTimestampNanos);
    }

    @Override
    public void read(BufferReader buffer) {
        this.clientTimestampNanos = buffer.readLong();
        this.serverTimestampNanos = buffer.readLong();
    }
}
