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
        buffer().writeLong(correlationId);

        buffer().writeShort(original.id());
        buffer().writeLong(original.timestamp());
        buffer().writeBytesArray(original.buffer().getBytes());
    }

    public ForwardPacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.correlationId = this.buffer().readLong();

        this.originalId = this.buffer().readShort();
        this.originalTimestamp = this.buffer().readLong();
        this.originalData = this.buffer().readBytesArray();
    }


}
