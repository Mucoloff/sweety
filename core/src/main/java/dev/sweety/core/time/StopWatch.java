package dev.sweety.core.time;

import java.util.concurrent.TimeUnit;

public class StopWatch {

    private long start = System.nanoTime();

    public void reset() {
        this.start = System.nanoTime();
    }

    public boolean hasPassedNano(long nano) {
        return nano() >= nano;
    }

    public long nano() {
        return System.nanoTime() - this.start;
    }

    public boolean hasPassedMillis(long millis) {
        return millis() >= millis;
    }

    public long millis() {
        return TimeUnit.NANOSECONDS.toMillis(nano());
    }
}