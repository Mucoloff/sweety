package dev.sweety.time;

import java.time.Instant;
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

