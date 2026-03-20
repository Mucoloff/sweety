package dev.sweety.util.logger.backend;

import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.level.LogLevel;

/**
 * Backend che non fa nulla: utile per disabilitare l'output di log.
 */
public record NoOpBackend() implements LoggerBackend {
    @Override
    public boolean isEnabled(LogLevel level) {
        return false;
    }

    @Override
    public void log(LogEvent event) {
        // no-op
    }
}

