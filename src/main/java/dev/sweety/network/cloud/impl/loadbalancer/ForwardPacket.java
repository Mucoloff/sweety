package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

@Getter
public class ForwardPacket extends Packet {

    private long correlationId;

    private byte originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public ForwardPacket(long correlationId, Packet original) {
        buffer.writeLong(correlationId);

        buffer.writeByte(original.getId());
        buffer.writeLong(original.getTimestamp());
        buffer.writeBytesArray(original.getData());
    }

    public ForwardPacket(byte id, long timestamp, byte[] data) {
        super(id, timestamp, data);
        this.correlationId = this.buffer.readLong();

        this.originalId = this.buffer.readByte();
        this.originalTimestamp = this.buffer.readLong();
        this.originalData = this.buffer.readBytesArray();
    }


}
