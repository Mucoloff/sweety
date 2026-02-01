package dev.sweety.netty.loadbalancer.common.metrics;

import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;

public record SmoothedLoad(
            float cpu,
            float ram,
            float cpuTotal,
            float ramTotal,
            float openFiles,
            float threadPressure,
            float systemLoad,
            NodeState state
    ) {

    public float[] data() {
        return new float[]{
                this.cpu,
                this.ram,
                this.cpuTotal,
                this.ramTotal,
                this.openFiles * 100f,
                this.threadPressure,
                this.systemLoad
        };
    }

    public static SmoothedLoad fromData(final NodeState state,final float... data) {
        if (data == null || data.length != 7) throw new IllegalArgumentException("Data array must contain exactly 7 elements.");
        return new SmoothedLoad(
                data[0],
                data[1],
                data[2],
                data[3],
                data[4] * 0.01f,
                data[5],
                data[6],
                state
        );
    }

}