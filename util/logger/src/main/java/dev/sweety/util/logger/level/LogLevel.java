package dev.sweety.util.logger.level;

public enum LogLevel {
    TRACE(10),
    DEBUG(20),
    INFO(30),
    WARN(40),
    ERROR(50);

    private final int severity;

    LogLevel(int severity) { this.severity = severity; }

    public int severity() { return severity; }
}
