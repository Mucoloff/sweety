package dev.sweety.netty.loadbalancer.packets;

import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;

@Getter
public class MetricsUpdatePacket extends Packet {

    private float cpuLoad;
    private float ramUsage;

    public MetricsUpdatePacket(float cpuLoad, float ramUsage) {
        this.buffer().writeVarInt((int) cpuLoad * 100);
        this.buffer().writeVarInt((int) ramUsage * 100);
    }

    public MetricsUpdatePacket(final int _id,final long _timestamp,final byte[] _data) {
        super(_id, _timestamp, _data);
        this.cpuLoad = this.buffer().readVarInt() * 0.01f;
        this.ramUsage = this.buffer().readVarInt() * 0.01f;
    }
}
