package dev.sweety.logger.backend;

import dev.sweety.logger.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public record SLF4JBackend(Logger logger) implements LoggerBackend {

    public SLF4JBackend(String loggerName) {
        this(LoggerFactory.getLogger(loggerName));
    }

    @Override
    public void log(LogLevel level, String loggerName, String profile, String formattedLine) {
        logger.makeLoggingEventBuilder(map(level)).log(formattedLine);
    }

    private static Level map(LogLevel level) {
        return switch (level) {
            case ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case INFO -> Level.INFO;
            case DEBUG -> Level.DEBUG;
            case TRACE -> Level.TRACE;
        };
    }
}

