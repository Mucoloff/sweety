package dev.sweety.netty.loadbalancer.refact.common.packet;

import dev.sweety.netty.loadbalancer.refact.common.metrics.SmoothedLoad;
import dev.sweety.netty.loadbalancer.refact.common.metrics.state.NodeState;
import dev.sweety.netty.packet.model.Packet;

public class MetricsUpdatePacket extends Packet {

    private static final float SCALE = 10_000f;

    private float cpu;       // EMA process / machine
    private float ram;       // EMA process / machine
    private float cpuTotal;  // system (debug)
    private float ramTotal;  // system (debug)
    private NodeState state; // HEALTHY / DEGRADED

    public MetricsUpdatePacket(final SmoothedLoad load) {
        this.buffer()
                .writeVarInt((int) (clamp(load.cpu()) * SCALE))
                .writeVarInt((int) (clamp(load.ram()) * SCALE))
                .writeVarInt((int) (clamp(load.cpuTotal()) * SCALE))
                .writeVarInt((int) (clamp(load.ramTotal()) * SCALE))
                .writeEnum(load.state());
    }

    public MetricsUpdatePacket(final int _id, final long _timestamp, final byte[] _data) {
        super(_id, _timestamp, _data);
        this.cpu = this.buffer().readVarInt() / SCALE;
        this.ram = this.buffer().readVarInt() / SCALE;
        this.cpuTotal = this.buffer().readVarInt() / SCALE;
        this.ramTotal = this.buffer().readVarInt() / SCALE;
        this.state = this.buffer().readEnum(NodeState.class);
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
}
