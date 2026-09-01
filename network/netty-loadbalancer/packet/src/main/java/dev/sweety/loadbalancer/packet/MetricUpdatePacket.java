package dev.sweety.loadbalancer.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.messaging.transport.TransportMode;
import dev.sweety.netty.packet.annotation.TransportHint;
import dev.sweety.netty.packet.model.Packet;

@TransportHint(TransportMode.UDP)
public final class MetricUpdatePacket extends Packet {

    private double cpuLoad;
    private long freeMemoryBytes;
    private double tps;
    private int activePlayers;
    private int alertsPerSecond;

    public MetricUpdatePacket() {}

    public MetricUpdatePacket(double cpuLoad, long freeMemoryBytes, double tps, int activePlayers, int alertsPerSecond) {
        this.cpuLoad = cpuLoad;
        this.freeMemoryBytes = freeMemoryBytes;
        this.tps = tps;
        this.activePlayers = activePlayers;
        this.alertsPerSecond = alertsPerSecond;
    }

    public static MetricUpdatePacket of(double cpuLoad, long freeMemoryBytes, double tps, int activePlayers, int alertsPerSecond) {
        return new MetricUpdatePacket(cpuLoad, freeMemoryBytes, tps, activePlayers, alertsPerSecond);
    }

    public double cpuLoad() { return cpuLoad; }
    public long freeMemoryBytes() { return freeMemoryBytes; }
    public double tps() { return tps; }
    public int activePlayers() { return activePlayers; }
    public int alertsPerSecond() { return alertsPerSecond; }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeDouble(cpuLoad);
        buffer.writeLong(freeMemoryBytes);
        buffer.writeDouble(tps);
        buffer.writeVarInt(activePlayers);
        buffer.writeVarInt(alertsPerSecond);
    }

    @Override
    public void read(BufferReader buffer) {
        this.cpuLoad = buffer.readDouble();
        this.freeMemoryBytes = buffer.readLong();
        this.tps = buffer.readDouble();
        this.activePlayers = buffer.readVarInt();
        this.alertsPerSecond = buffer.readVarInt();
    }
}
