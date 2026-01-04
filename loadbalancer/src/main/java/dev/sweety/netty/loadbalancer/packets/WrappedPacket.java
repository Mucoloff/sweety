package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@Getter
public class WrappedPacket extends Packet {

    private long correlationId;
    private boolean closing;

    private short originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public WrappedPacket(long correlationId, boolean closing, short originalId, Packet original) {
        buffer().writeVarLong(correlationId);
        buffer().writeBoolean(closing);

        buffer().writeShort(originalId);
        buffer().writeVarLong(original.timestamp());
        buffer().writeByteArray(original.buffer().getBytes());
    }

    public WrappedPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readVarLong();
        this.closing = this.buffer().readBoolean();

        this.originalId = this.buffer().readShort();
        this.originalTimestamp = this.buffer().readVarLong();
        this.originalData = this.buffer().readByteArray();
    }

}
