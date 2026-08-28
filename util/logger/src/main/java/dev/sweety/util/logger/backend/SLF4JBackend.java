package dev.sweety.util.logger.backend;

import dev.sweety.exception.ExceptionUtils;
import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.util.LogArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public record SLF4JBackend(Logger logger) implements LoggerBackend {

    public SLF4JBackend(String loggerName) {
        this(LoggerFactory.getLogger(loggerName));
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return logger.isEnabledForLevel(map(level));
    }

    @Override
    public void log(LogEvent event) {
        if (event.rawArgs() == null || event.rawArgs().length == 0) return;

        String message = LogArguments.formatMessage(event.rawArgs());
        Throwable cause = LogArguments.trailingThrowable(event.rawArgs());
        if (cause != null) message = message + "\n" + ExceptionUtils.getStackTrace(cause);

        logger.atLevel(map(event.level())).log(message);
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

