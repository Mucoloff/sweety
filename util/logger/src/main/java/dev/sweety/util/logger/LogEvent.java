package dev.sweety.util.logger;

import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.profile.LogProfile;
import dev.sweety.util.logger.util.LogArguments;

public record LogEvent(LogLevel level, String loggerName, LogProfile profile, Object[] rawArgs,
                       String pattern, Object[] params) {

    public LogEvent(LogLevel level, String loggerName, LogProfile profile, Object[] rawArgs) {
        this(level, loggerName, profile, rawArgs,
                LogArguments.isPattern(rawArgs) ? LogArguments.pattern(rawArgs) : null,
                LogArguments.isPattern(rawArgs) ? LogArguments.params(rawArgs) : new Object[0]);
    }
}
