package dev.sweety.math.stat;

/**
 * High-performance, zero-allocation Exponential Moving Average (EMA).
 * Backed by primitive double precision with thread-safe synchronized updates.
 */
public final class EMA {

    private final double alpha; // Smoothing factor: 0 < alpha <= 1
    private volatile double value;
    private volatile boolean initialized;

    public EMA(double alpha) {
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha must be in (0, 1], got: " + alpha);
        }
        this.alpha = alpha;
        this.value = 0.0;
        this.initialized = false;
    }

    /**
     * Ingests a new sample and updates the moving average.
     * Zero-allocation primitive math.
     *
     * @param sample input observation
     * @return current smoothed average after applying sample
     */
    public synchronized double update(double sample) {
        if (!initialized) {
            value = sample;
            initialized = true;
        } else {
            value = alpha * sample + (1.0 - alpha) * value;
        }
        return value;
    }

    /**
     * Returns the current smoothed value, or 0.0 if not yet initialized.
     */
    public double get() {
        return value;
    }

    /**
     * Checks if at least one sample has been ingested.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resets the moving average to uninitialized state.
     */
    public synchronized void reset() {
        this.initialized = false;
        this.value = 0.0;
    }

    public double alpha() {
        return alpha;
    }
}
