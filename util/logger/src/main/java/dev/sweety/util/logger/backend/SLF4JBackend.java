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
        if (event.rawArgs() == null || event.rawArgs().length == 0) {
            return;
        }

        // Production-grade safety: Ensure MDC is cleaned up to prevent context bleeding
        // We use MDC.put/remove instead of addKeyValue ensures compatibility with standard %X{profile} layouts
        if (event.profile() != null) {
            MDC.put("profile", event.profile().fullPath());
        }
        
        try {
            var builder = logger.atLevel(map(event.level()));

            if (event.pattern() != null) {
                builder.log(event.pattern(), event.params());
            } else {
                builder.log(String.valueOf(event.rawArgs()[0]));
            }
        } finally {
            if (event.profile() != null) {
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

