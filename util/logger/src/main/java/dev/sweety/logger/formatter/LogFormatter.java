package dev.sweety.logger.formatter;

import dev.sweety.logger.level.LogLevel;
import dev.sweety.logger.profile.LogProfile;

public interface LogFormatter {
    /**
     * Formats the log message.
     *
     * @param level      The log level
     * @param loggerName The name of the logger
     * @param profile    The current thread profile (or null/empty)
     * @param args       The raw arguments passed to the logger
     * @return The fully formatted string ready for output
     */
    String format(LogLevel level, String loggerName, LogProfile profile, Object[] args);
}
