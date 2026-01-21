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

    //todo array of floats for custom metrics?
    private float cpu;       // EMA process / machine
    private float ram;       // EMA process / machine
    private float cpuTotal;  // system (debug)
    private float ramTotal;  // system (debug)

    private NodeState state; // HEALTHY / DEGRADED
    private Map<Integer, Float> packetTimings; // packet ID -> EMA latency

    public MetricsUpdatePacket(final SmoothedLoad load, Map<Integer, EMA> packetTimings) {
        this.buffer()
                .writeVarInt((int) (clamp(load.cpu()) * SCALE))
                .writeVarInt((int) (clamp(load.ram()) * SCALE))
                .writeVarInt((int) (clamp(load.cpuTotal()) * SCALE))
                .writeVarInt((int) (clamp(load.ramTotal()) * SCALE))
                .writeEnum(load.state())
                .writeMap(packetTimings, (buf, pair) -> {
                    final EMA ema = pair.value();
                    buf.writeVarInt(pair.key()).writeVarInt((int) (clamp(ema.get()) * SCALE));
                });
    }

    public MetricsUpdatePacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.cpu = this.buffer().readVarInt() / SCALE;
        this.ram = this.buffer().readVarInt() / SCALE;
        this.cpuTotal = this.buffer().readVarInt() / SCALE;
        this.ramTotal = this.buffer().readVarInt() / SCALE;
        this.state = this.buffer().readEnum(NodeState.class);
        this.packetTimings = this.buffer().readMap(PacketBuffer::readVarInt, buf -> buf.readVarInt() / SCALE, HashMap::new);

    }

    public static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public float cpu() {
        return cpu;
    }

    public float ram() {
        return ram;
    }

    public float cpuTotal() {
        return cpuTotal;
    }

    public float ramTotal() {
        return ramTotal;
    }

    public NodeState state() {
        return state;
    }

    public Map<Integer, Float> packetTimings() {
        return packetTimings;
    }
}
