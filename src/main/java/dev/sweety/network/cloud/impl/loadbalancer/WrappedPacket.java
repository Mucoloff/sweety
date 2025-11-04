package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

@Getter
public class WrappedPacket extends Packet {

    private long correlationId;
    private boolean closing;

    private byte originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public WrappedPacket(long correlationId, boolean closing, byte originalId, Packet original) {
        buffer.writeLong(correlationId);
        buffer.writeBoolean(closing);

        buffer.writeByte(originalId);
        buffer.writeLong(original.getTimestamp());
        buffer.writeBytesArray(original.getData());
    }

    public WrappedPacket(byte id, long timestamp, byte[] data) {
        super(id, timestamp, data);
        this.correlationId = this.buffer.readLong();
        this.closing = this.buffer.readBoolean();

        this.originalId = this.buffer.readByte();
        this.originalTimestamp = this.buffer.readLong();
        this.originalData = this.buffer.readBytesArray();
    }

}
