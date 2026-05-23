package dev.sweety.netty.metrics;

import java.util.concurrent.atomic.AtomicReference;

public final class EMA {

    private final double alpha; // 0 < alpha <= 1
    private final AtomicReference<Double> value = new AtomicReference<>(0.0);
    private volatile boolean initialized = false;

    public EMA(double alpha) {
        this.alpha = alpha;
    }

    public synchronized double update(double sample) {
        if (!initialized) {
            value.set(sample);
            initialized = true;
        } else {
            value.updateAndGet(v -> alpha * sample + (1 - alpha) * v);
        }
        return value.get();
    }

    public double get() {
        return value.get();
    }

    public void reset() {
        this.initialized = false;
        this.value.set(0.0);
    }
}
