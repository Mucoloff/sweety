package dev.sweety.core.time;

import java.util.concurrent.TimeUnit;

public class StopWatch {

    private long startNano = System.nanoTime();

    public void reset() {
        this.startNano = System.nanoTime();
    }

    public boolean hasPassed(long millis) {
        long elapsedNanos = System.nanoTime() - this.startNano;
        return elapsedNanos >= TimeUnit.MILLISECONDS.toNanos(millis);
    }

    public long getTime() {
        long elapsedNanos = System.nanoTime() - this.startNano;
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }
}