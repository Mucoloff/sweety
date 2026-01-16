package dev.sweety.netty.loadbalancer.common.metrics.state;

public final class LoadGate {

    private NodeState state = NodeState.HEALTHY;

    private static final float CPU_OUT = 0.70f;
    private static final float CPU_IN  = 0.55f;

    private static final float RAM_OUT = 0.75f;
    private static final float RAM_IN  = 0.60f;

    public NodeState update(float cpu, float ram) {
        switch (state) {
            case HEALTHY -> {
                if (cpu > CPU_OUT || ram > RAM_OUT) state = NodeState.DEGRADED;
            }
            case DEGRADED -> {
                if (cpu < CPU_IN && ram < RAM_IN) state = NodeState.HEALTHY;
            }
        }
        return state;
    }

    public void reset() {
        this.state = NodeState.HEALTHY;
    }
}
