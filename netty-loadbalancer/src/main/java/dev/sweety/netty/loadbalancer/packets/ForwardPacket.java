package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@Getter
public class ForwardPacket extends Packet {

    private long correlationId;

    private int originalId;
    private long originalTimestamp;
    private byte[] originalData;

    public ForwardPacket(final long correlationId,final  Packet original) {
        buffer().writeVarLong(correlationId);

        buffer().writeVarInt(original.id());
        buffer().writeVarLong(original.timestamp());
        buffer().writeByteArray(original.buffer().getBytes());
    }

    public ForwardPacket(final int _id,final long _timestamp,final byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readVarLong();

        this.originalId = this.buffer().readVarInt();
        this.originalTimestamp = this.buffer().readVarLong();
        this.originalData = this.buffer().readByteArray();
    }

}
