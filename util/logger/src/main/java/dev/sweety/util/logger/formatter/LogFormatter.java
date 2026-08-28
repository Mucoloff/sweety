package dev.sweety.util.logger.formatter;

import dev.sweety.exception.ExceptionUtils;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.util.LogArguments;

public interface LogFormatter {
    /**
     * Formats the log message.
     *
     * @param level      The log level
     * @param loggerName The name of the logger (may include child path, e.g. "Client/Auth")
     * @param args       The raw arguments passed to the logger
     * @return The fully formatted string ready for output
     */
    String format(LogLevel level, String loggerName, Object[] args);

    static String buildMessage(Object[] args) {
        if (args == null || args.length == 0) return "";

        if (!LogArguments.isPattern(args)) {
            return LogArguments.formatMessage(args);
        }

        String message = LogArguments.formatMessage(args);
        Throwable cause = LogArguments.trailingThrowable(args);
        if (cause == null) return message;
        return message + "\n" + ExceptionUtils.getStackTrace(cause);
    }
}
