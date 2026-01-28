package dev.sweety.core.time;

import java.util.concurrent.TimeUnit;

public final class StopWatch {

    private long start;

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
}
