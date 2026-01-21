package dev.sweety.netty.loadbalancer.common.metrics;

import dev.sweety.netty.loadbalancer.common.metrics.state.NodeState;

//todo array of floats for custom metrics?
public record SmoothedLoad(
            float cpu,        // processo / macchina (EMA)
            float ram,        // processo / macchina (EMA)
            float cpuTotal,   // sistema
            float ramTotal,   // sistema
            NodeState state
    ) {}