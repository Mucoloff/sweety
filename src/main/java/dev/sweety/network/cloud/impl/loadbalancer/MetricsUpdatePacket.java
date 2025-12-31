package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

@Getter
public class MetricsUpdatePacket extends Packet {

    private float cpuLoad;
    private float ramUsage;

    public MetricsUpdatePacket(float cpuLoad, float ramUsage) {
        this.buffer().writeFloat(cpuLoad);
        this.buffer().writeFloat(ramUsage);
    }

    public MetricsUpdatePacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
        this.cpuLoad = this.buffer().readFloat();
        this.ramUsage = this.buffer().readFloat();
    }
}
