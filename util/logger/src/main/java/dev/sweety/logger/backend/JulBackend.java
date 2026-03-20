package dev.sweety.logger.backend;

import dev.sweety.logger.LogEvent;
import dev.sweety.logger.level.LogLevel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backend basato su java.util.logging (JUL), presente nel JDK.
 * Non richiede dipendenze esterne e mantiene l'indipendenza da SLF4J.
 */
public record JulBackend(Logger logger) implements LoggerBackend {

    public JulBackend(String loggerName) {
        this(Logger.getLogger(loggerName));
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return logger.isLoggable(map(level));
    }

    @Override
    public void log(LogEvent event) {
        final Level level = map(event.getLevel());
        if (!logger.isLoggable(level)) return;

        if (event.getRawArgs() == null || event.getRawArgs().length == 0) return;

        if (event.getPattern() != null) {
            logger.log(level, event.getPattern(), event.getParams());
        } else {
            logger.log(level, String.valueOf(event.getRawArgs()[0]));
        }
    }

    private static Level map(LogLevel level) {
        return switch (level) {
            case ERROR -> Level.SEVERE;
            case WARN -> Level.WARNING;
            case INFO -> Level.INFO;
            case DEBUG -> Level.FINE;
            case TRACE -> Level.FINER;
        };
    }
}

