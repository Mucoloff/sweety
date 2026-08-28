package dev.sweety.time;

import java.util.concurrent.TimeUnit;

public final class StopWatch {

    private long start = 0;

    public StopWatch() {
        reset();
    }

    public void reset() {
        this.start = System.nanoTime();
    }

    public long elapsedNanos() {
        return System.nanoTime() - start;
    }

    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos());
    }

    public boolean hasPassedNanos(long nanos) {
        return elapsedNanos() >= nanos;
    }

    public boolean hasPassedMillis(long millis) {
        return elapsedMillis() >= millis;
    }

    public boolean hasPassedNanos(long nanos, boolean reset) {
        boolean passed = elapsedNanos() >= nanos;
        if (reset && passed) reset();
        return passed;
    }

    public boolean hasPassedMillis(long millis, boolean reset) {
        boolean passed = elapsedMillis() >= millis;
        if (reset && passed) reset();
        return passed;
    }
}
