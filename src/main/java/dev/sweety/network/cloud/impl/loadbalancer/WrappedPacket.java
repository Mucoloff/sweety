package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

@Getter
public class WrappedPacket extends Packet {

    private long correlationId;
    private boolean closing;

    private short originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public WrappedPacket(long correlationId, boolean closing, short originalId, Packet original) {
        buffer().writeLong(correlationId);
        buffer().writeBoolean(closing);

        buffer().writeShort(originalId);
        buffer().writeLong(original.timestamp());
        buffer().writeBytesArray(original.buffer().getBytes());
    }

    public WrappedPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readLong();
        this.closing = this.buffer().readBoolean();

        this.originalId = this.buffer().readShort();
        this.originalTimestamp = this.buffer().readLong();
        this.originalData = this.buffer().readBytesArray();
    }

}
