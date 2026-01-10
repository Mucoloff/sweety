package dev.sweety.core.logger.backend;

import dev.sweety.core.logger.LogLevel;

// Default console backend (stdout/stderr)
public class ConsoleBackend implements LoggerBackend {
    @Override
    public void log(LogLevel level, String loggerName, String profile, String formattedLine) {
        if (level == LogLevel.ERROR) {
            System.err.println(formattedLine);
        } else {
            System.out.println(formattedLine);
        }
    }
}
