package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@Getter
public class WrappedPacket extends Packet {

    private long correlationId;
    private boolean closing;

    private int originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public WrappedPacket(long correlationId, boolean closing, int originalId, Packet original) {
        buffer().writeVarLong(correlationId);
        buffer().writeBoolean(closing);

        buffer().writeVarInt(originalId);
        buffer().writeVarLong(original.timestamp());
        buffer().writeByteArray(original.buffer().getBytes());
    }

    public WrappedPacket(final int _id,final long _timestamp,final byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readVarLong();
        this.closing = this.buffer().readBoolean();

        this.originalId = this.buffer().readVarInt();
        this.originalTimestamp = this.buffer().readVarLong();
        this.originalData = this.buffer().readByteArray();
    }

}
