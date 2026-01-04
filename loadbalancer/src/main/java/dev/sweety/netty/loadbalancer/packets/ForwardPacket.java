package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@Getter
public class ForwardPacket extends Packet {

    private long correlationId;

    private short originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public ForwardPacket(long correlationId, Packet original) {
        buffer().writeVarLong(correlationId);

        buffer().writeShort(original.id());
        buffer().writeVarLong(original.timestamp());
        buffer().writeByteArray(original.buffer().getBytes());
    }

    public ForwardPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readVarLong();

        this.originalId = this.buffer().readShort();
        this.originalTimestamp = this.buffer().readVarLong();
        this.originalData = this.buffer().readByteArray();
    }


}
