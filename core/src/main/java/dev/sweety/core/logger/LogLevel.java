package dev.sweety.core.logger;

import dev.sweety.core.color.AnsiColor;

public enum LogLevel {
    INFO(AnsiColor.WHITE_BRIGHT),
    WARN(AnsiColor.YELLOW_BRIGHT),
    ERROR(AnsiColor.RED_BRIGHT),
    DEBUG(AnsiColor.PURPLE_BRIGHT),
    TRACE(AnsiColor.RESET);

    private final AnsiColor color;

    LogLevel(final AnsiColor color) {
        this.color = color;
    }

    public AnsiColor color() {
        return color;
    }
}
