package dev.sweety.loadbalancer.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.annotation.TransportHint;
import dev.sweety.netty.packet.model.Packet;

@TransportHint(TransportMode.UDP)
public final class PingPacket extends Packet {

    private long timestampNanos;

    public PingPacket() {}

    public PingPacket(long timestampNanos) {
        this.timestampNanos = timestampNanos;
    }

    public static PingPacket of(long timestampNanos) {
        return new PingPacket(timestampNanos);
    }

    public long timestampNanos() {
        return timestampNanos;
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeLong(timestampNanos);
    }

    @Override
    public void read(BufferReader buffer) {
        this.timestampNanos = buffer.readLong();
    }
}
