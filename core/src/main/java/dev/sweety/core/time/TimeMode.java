package dev.sweety.core.time;

import java.util.function.LongSupplier;

public enum TimeMode {
    MILLIS(System::currentTimeMillis),
    NANO(System::nanoTime),
    NONE(() -> 0L);

    private final LongSupplier clock;

    TimeMode(LongSupplier clock) {
        this.clock = clock;
    }

    public long now() {
        return clock.getAsLong();
    }
}

