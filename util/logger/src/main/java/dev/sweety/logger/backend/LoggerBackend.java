package dev.sweety.logger.backend;

import dev.sweety.logger.LogEvent;
import dev.sweety.logger.level.LogLevel;

// Backend SPI
public interface LoggerBackend {
    boolean isEnabled(LogLevel level);

    void log(LogEvent event);
}
