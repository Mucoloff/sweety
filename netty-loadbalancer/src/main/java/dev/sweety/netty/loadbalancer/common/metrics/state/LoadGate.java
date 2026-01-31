package dev.sweety.netty.loadbalancer.common.metrics.state;


public final class LoadGate {

    private volatile NodeState state = NodeState.HEALTHY;

    private final float min, max;
    private final float[] in;
    private final float[] out;
    private final float[] weights;

    private final int count;

    public LoadGate(float min, float max, Limits... limits) {
        if (min >= max) throw new IllegalArgumentException("min must be less than max");
        this.min = min;
        this.max = max;
        if (limits == null || (this.count = limits.length) == 0) throw new IllegalArgumentException("limits is empty");
        this.in = new float[this.count];
        this.out = new float[this.count];
        this.weights = new float[this.count];
        for (int i = 0; i < this.count; i++) {
            this.in[i] = limits[i].in();
            this.out[i] = limits[i].out();
            this.weights[i] = limits[i].weight();
        }
    }

    public synchronized NodeState update(float... metrics) {
        if (metrics == null) throw new IllegalArgumentException("metrics null");
        if (metrics.length != in.length)
            throw new IllegalArgumentException("metrics length mismatch: expected " + in.length + " got " + metrics.length);

        switch (state) {
            case HEALTHY -> {
                if (aboveOut(metrics)) state = NodeState.DEGRADED;
            }
            case DEGRADED -> {
                if (belowIn(metrics)) state = NodeState.HEALTHY;
            }
        }
        return state;
    }

    public synchronized void reset() {
        this.state = NodeState.HEALTHY;
    }

    public NodeState get() {
        return state;
    }

    private boolean aboveOut(float... metrics) {
        float value = 0f;
        for (int i = 0; i < metrics.length && i < this.count; i++) {
            if (metrics[i] > out[i]) {
                value += weights[i];
            }
        }
        return value >= max;
    }

    private boolean belowIn(float... metrics) {
        float value = 0f;
        for (int i = 0; i < metrics.length && i < this.count; i++) {
            if (metrics[i] >= in[i]) {
                value += weights[i];
            }
        }
        return value <= min;
    }

    @Override
    public String toString() {
        return "LoadGate{" +
                "state=" + state +
                ", min=" + min +
                ", max=" + max +
                '}';
    }

    public record Limits(float in, float out, float weight) {
        public Limits {
            if (in >= out) throw new IllegalArgumentException("in must be less than out");
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        }
    }
}