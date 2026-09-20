package dev.sweety.netty.metrics;

/**
 * Backward-compatible wrapper delegating to {@link dev.sweety.math.stat.EMA}.
 * Eliminates boxed AtomicReference allocations while preserving API for netty-saas and loadbalancer.
 */
public final class EMA {

    private final dev.sweety.math.stat.EMA delegate;

    public EMA(double alpha) {
        this.delegate = new dev.sweety.math.stat.EMA(alpha);
    }

    public double update(double sample) {
        return delegate.update(sample);
    }

    public double get() {
        return delegate.get();
    }

    public void reset() {
        delegate.reset();
    }

    public boolean isInitialized() {
        return delegate.isInitialized();
    }

    public dev.sweety.math.stat.EMA unwrap() {
        return delegate;
    }
}
