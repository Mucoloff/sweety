package dev.sweety.netty.metrics;


import dev.sweety.netty.metrics.state.NodeState;

public record SmoothedLoad(
        double cpu,
        double ram,
        double cpuTotal,
        double ramTotal,
        double openFiles,
        double threadPressure,
        double systemLoad,
        NodeState state
) {
    public static final int SIZE = 7;

    public double[] data() {
        return new double[]{
                this.cpu,
                this.ram,
                this.cpuTotal,
                this.ramTotal,
                this.openFiles * 100f,
                this.threadPressure,
                this.systemLoad
        };
    }

    public static SmoothedLoad fromData(final NodeState state,final double... data) {
        if (data == null || data.length != SIZE) throw new IllegalArgumentException("Data array must contain exactly 7 elements.");
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