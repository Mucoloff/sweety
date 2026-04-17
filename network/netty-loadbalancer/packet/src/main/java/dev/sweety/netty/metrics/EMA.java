package dev.sweety.netty.metrics;

public final class EMA {

    private final double alpha; // 0 < alpha <= 1
    private volatile double value;
    private volatile boolean initialized = false;

    public EMA(double alpha) {
        this.alpha = alpha;
    }

    public synchronized double update(double sample) {
        if (!initialized) {
            value = sample;
            initialized = true;
        } else {
            value = alpha * sample + (1f - alpha) * value;
        }
        return value;
    }

    public double get() {
        return value;
    }

    public void reset() {
        this.initialized = false;
        this.value = 0f;
    }
}
