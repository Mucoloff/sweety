package dev.sweety.network.cloud.impl.loadbalancer;

import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;

@Getter
public class MetricsUpdatePacket extends Packet {

    private double cpuLoad;
    private double ramUsage;

    public MetricsUpdatePacket(double cpuLoad, double ramUsage) {
        this.buffer.writeDouble(cpuLoad);
        this.buffer.writeDouble(ramUsage);
    }

    public MetricsUpdatePacket(byte id, long timestamp, byte[] data) {
        super(id, timestamp, data);
        this.cpuLoad = this.buffer.readDouble();
        this.ramUsage = this.buffer.readDouble();
    }
}
