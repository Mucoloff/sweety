package dev.sweety.network.cloud.packet.model;

import dev.sweety.core.time.TimeUtils;
import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import lombok.Getter;

@Getter
public abstract class Packet {

    protected final byte id;
    protected final long timestamp;
    protected final PacketBuffer buffer;

    public Packet() {
        this((byte) -1, -1L);
    }

    public Packet(long timestamp) {
        this((byte) -1, timestamp);
    }

    public Packet(byte id, long timestamp) {
        this.id = id;
        this.timestamp = timestamp;
        this.buffer = new PacketBuffer();
    }

    // (decoder)
    public Packet(byte id, long timestamp, byte[] data) {
        this.id = id;
        this.timestamp = timestamp;
        this.buffer = new PacketBuffer(data);
        this.buffer.markReaderIndex();
    }

    public byte[] getData() {
        return this.buffer.getBytes();
    }

    public String name() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        String time = " [" + TimeUtils.date(timestamp, "dd-mm-yyyy hh:MM:ss") + "] ";
        return name() + " (" + id + ")" + (timestamp > 0 ? time : " ") + "- " + buffer.readableBytes() + " bytes";
    }

}
