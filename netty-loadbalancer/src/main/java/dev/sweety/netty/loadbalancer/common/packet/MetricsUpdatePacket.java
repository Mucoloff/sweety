package dev.sweety.netty.loadbalancer.common.packet;

import dev.sweety.netty.loadbalancer.common.metrics.EMA;
import dev.sweety.netty.loadbalancer.common.metrics.SmoothedLoad;
import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;

import java.util.HashMap;
import java.util.Map;


public class MetricsUpdatePacket extends Packet {

    private static final float SCALE = 10_000f;

    private SmoothedLoad load;

    private Map<Integer, Float> packetTimings; // packet ID -> EMA latency

    public MetricsUpdatePacket(final SmoothedLoad load, Map<Integer, EMA> packetTimings) {
        this.buffer()
                .writePercentual(load.cpu(), SCALE)
                .writePercentual(load.ram(), SCALE)
                .writePercentual(load.cpuTotal(), SCALE)
                .writePercentual(load.ramTotal(), SCALE)
                .writeFloat(load.openFiles())
                .writePercentual(load.threadPressure(), SCALE)
                .writeFloat(load.systemLoad())
                .writeEnum(load.state())
                .writeMap(packetTimings, (buf, pair) -> {
                    final EMA ema = pair.value();
                    buf.writeVarInt(pair.key()).writePercentual(ema.get(), SCALE);
                });
    }

    public MetricsUpdatePacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);

        this.load = new SmoothedLoad(
                this.buffer().readPercentual(SCALE),
                this.buffer().readPercentual(SCALE),
                this.buffer().readPercentual(SCALE),
                this.buffer().readPercentual(SCALE),
                this.buffer().readFloat(),
                this.buffer().readPercentual(SCALE),
                this.buffer().readFloat(),
                this.buffer().readEnum(NodeState.class)
        );


        this.packetTimings = this.buffer().readMap(PacketBuffer::readVarInt, buf -> buf.readPercentual(SCALE), HashMap::new);
    }

    public float cpu() {
        return load.cpu();
    }

    public float ram() {
        return load.ram();
    }

    public float cpuTotal() {
        return load.cpuTotal();
    }

    public float ramTotal() {
        return load.ramTotal();
    }

    public float openFiles() {
        return load.openFiles();
    }

    public float threadPressure() {
        return load.threadPressure();
    }

    public float systemLoad() {
        return load.systemLoad();
    }

    public NodeState state() {
        return load.state();
    }

    public Map<Integer, Float> packetTimings() {
        return packetTimings;
    }
}
