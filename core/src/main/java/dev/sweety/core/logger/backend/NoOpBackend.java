package dev.sweety.core.logger.backend;

import dev.sweety.core.logger.LogLevel;

/**
 * Backend che non fa nulla: utile per disabilitare l'output di log.
 */
public class NoOpBackend implements LoggerBackend {
    @Override
    public void log(LogLevel level, String loggerName, String profile, String formattedLine) {
        // no-op
    }
}

