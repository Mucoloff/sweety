package dev.sweety.netty.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.metrics.EMA;
import dev.sweety.netty.metrics.SmoothedLoad;
import dev.sweety.netty.metrics.state.NodeState;
import dev.sweety.netty.packet.model.Packet;

import java.util.HashMap;
import java.util.Map;

public class MetricsUpdatePacket extends Packet {

    private static final double SCALE = 10_000.0;

    private SmoothedLoad load;
    private Map<Integer, Double> packetTimings; // packet ID -> EMA latency

    public MetricsUpdatePacket() {
    }

    public MetricsUpdatePacket(final SmoothedLoad load, Map<Integer, EMA> packetTimings) {
        this.load = load;
        this.packetTimings = new HashMap<>();
        if (packetTimings != null) {
            packetTimings.forEach((k, v) -> this.packetTimings.put(k, v.get()));
        }
    }

    @Override
    public void write(final BufferWriter buffer) {
        buffer.writePercentual(load.cpu(), SCALE)
                .writePercentual(load.ram(), SCALE)
                .writePercentual(load.cpuTotal(), SCALE)
                .writePercentual(load.ramTotal(), SCALE)
                .writePercentual(load.openFiles(), SCALE * 100)
                .writePercentual(load.threadPressure(), SCALE)
                .writePercentual(load.systemLoad(), SCALE)
                .writeEnum(load.state());
        buffer.writeMap(packetTimings, (buf, pair) -> {
            buf.writeVarInt(pair.key()).writePercentual(pair.value(), SCALE);
        });
    }

    @Override
    public void read(final BufferReader buffer) {
        this.load = new SmoothedLoad(
                buffer.readPercentual(SCALE),
                buffer.readPercentual(SCALE),
                buffer.readPercentual(SCALE),
                buffer.readPercentual(SCALE),
                buffer.readPercentual(SCALE * 100),
                buffer.readPercentual(SCALE),
                buffer.readPercentual(SCALE),
                buffer.readEnum(NodeState.class)
        );
        this.packetTimings = buffer.readMap(BufferReader::readVarInt, buf -> buf.readPercentual(SCALE), HashMap::new);
    }

    public SmoothedLoad load() {
        return load;
    }

    public double cpu() {
        return load.cpu();
    }

    public double ram() {
        return load.ram();
    }

    public double cpuTotal() {
        return load.cpuTotal();
    }

    public double ramTotal() {
        return load.ramTotal();
    }

    public double openFiles() {
        return load.openFiles();
    }

    public double threadPressure() {
        return load.threadPressure();
    }

    public double systemLoad() {
        return load.systemLoad();
    }

    public NodeState state() {
        return load.state();
    }

    public Map<Integer, Double> packetTimings() {
        return packetTimings;
    }
}
