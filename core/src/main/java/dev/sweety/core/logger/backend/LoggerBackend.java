package dev.sweety.core.logger.backend;

import dev.sweety.core.logger.LogLevel;

// Backend SPI
public interface LoggerBackend {
    void log(LogLevel level, String loggerName, String profile, String formattedLine);
}
