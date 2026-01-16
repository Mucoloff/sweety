package dev.sweety.netty.loadbalancer.refact.common.metrics;

public final class EMA {

    private final float alpha; // 0 < alpha <= 1
    private float value;
    private boolean initialized = false;

    public EMA(float alpha) {
        this.alpha = alpha;
    }

    public float update(float sample) {
        if (!initialized) {
            value = sample;
            initialized = true;
        } else {
            value = alpha * sample + (1f - alpha) * value;
        }
        return value;
    }

    public float get() {
        return value;
    }

    public void reset() {
        this.initialized = false;
        this.value = 0f;
    }
}
