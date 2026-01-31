package dev.sweety.netty.loadbalancer.common.metrics.state;

import java.util.Arrays;
import java.util.stream.IntStream;

public final class LoadGate {

    private NodeState state = NodeState.HEALTHY;

    // soglie per metrica (stessa lunghezza di in/out)
    private final float min, max;
    private final float[] in;
    private final float[] out;
    private final float[] weights;

    public LoadGate(float min, float max, Limits... limits) {
        if (min >= max) throw new IllegalArgumentException("min must be less than max");
        this.min = min;
        this.max = max;
        if (limits == null || limits.length == 0) throw new IllegalArgumentException("limits is empty");
        this.in = new float[limits.length];
        this.out = new float[limits.length];
        this.weights = new float[limits.length];
        for (int i = 0; i < limits.length; i++) {
            this.in[i] = limits[i].in();
            this.out[i] = limits[i].out();
            this.weights[i] = limits[i].weight();
        }
    }

    public LoadGate(float min, float max) {
        this(min, max,
                new Limits(0.55f, 0.70f, 0.6f), // CPU
                new Limits(0.60f, 0.75f, 0.4f)); // RAM
    }

    public LoadGate(){
        this(0.35f, 0.55f);
    }

    public NodeState update(float... metrics) {
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

    public void reset() {
        this.state = NodeState.HEALTHY;
    }

    private boolean aboveOut(float... metrics) {
        float value = 0f;
        for (int i = 0; i < metrics.length; i++) {
            if (metrics[i] > out[i]) {
                value += weights[i];
            }
        }
        return value >= max;
    }

    private boolean belowIn(float... metrics) {
        float value = 0f;
        for (int i = 0; i < metrics.length; i++) {
            if (metrics[i] >= in[i]) {
                value += weights[i];
            }
        }
        return value <= min;
    }

    public record Limits(float in, float out, float weight) {
    }
}