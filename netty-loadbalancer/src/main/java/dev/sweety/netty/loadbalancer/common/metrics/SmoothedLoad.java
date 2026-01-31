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
    ) {}