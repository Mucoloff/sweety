package dev.sweety.logger.backend;

import dev.sweety.logger.LogLevel;

// Backend SPI
public interface LoggerBackend {
    void log(LogLevel level, String loggerName, String profile, String formattedLine);
}
