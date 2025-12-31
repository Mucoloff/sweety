package dev.sweety.core.event.info;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Priority {
    LOWEST(100),
    LOW(200),
    NORMAL(300),
    HIGH(400),
    HIGHEST(500),
    MONITOR(600);

    public static final Priority[] VALUES = values();

    private final int value;
}