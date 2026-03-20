package dev.sweety.util.logger.backend;

import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.level.LogLevel;

// Backend SPI
public interface LoggerBackend {
    boolean isEnabled(LogLevel level);

    void log(LogEvent event);
}
