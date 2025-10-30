package dev.sweety.network.cloud.packet.model;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import lombok.Getter;

@Getter
public abstract class Packet implements IPacket {
    protected final byte id;
    protected final Long timestamp;
    protected final PacketBuffer buffer;

    public Packet(byte id, Long timestamp, PacketBuffer buffer) {
        this.id = id;
        this.timestamp = timestamp;
        this.buffer = buffer;
    }

    public Packet(Packet packet) {
        this(packet.id, packet.timestamp, packet.buffer);
    }

    @Override
    public byte[] getData() {
        return this.buffer.getBytes();
    }
}
