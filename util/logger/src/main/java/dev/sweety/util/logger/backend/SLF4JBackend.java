package dev.sweety.util.logger.backend;

import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.level.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
        if (event.getRawArgs() == null || event.getRawArgs().length == 0) {
            return;
        }

        // Production-grade safety: Ensure MDC is cleaned up to prevent context bleeding
        // We use MDC.put/remove instead of addKeyValue ensures compatibility with standard %X{profile} layouts
        if (event.getProfile() != null) {
            MDC.put("profile", event.getProfile().getFullPath());
        }
        
        try {
            var builder = logger.atLevel(map(event.getLevel()));

            if (event.getPattern() != null) {
                builder.log(event.getPattern(), event.getParams());
            } else {
                builder.log(String.valueOf(event.getRawArgs()[0]));
            }
        } finally {
            if (event.getProfile() != null) {
                MDC.remove("profile");
            }
        }
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

