package dev.sweety.logger;

import dev.sweety.logger.level.LogLevel;
import dev.sweety.logger.profile.LogProfile;
import dev.sweety.logger.util.LogArguments;
import lombok.Getter;

@Getter
public class LogEvent {
    private final LogLevel level;
    private final String loggerName;
    private final LogProfile profile;
    private final Object[] rawArgs;
    
    private final String pattern;
    private final Object[] params;

    public LogEvent(LogLevel level, String loggerName, LogProfile profile, Object[] rawArgs) {
        this.level = level;
        this.loggerName = loggerName;
        this.profile = profile;
        this.rawArgs = rawArgs;

        if (LogArguments.isPattern(rawArgs)) {
            this.pattern = LogArguments.pattern(rawArgs);
            this.params = LogArguments.params(rawArgs);
        } else {
            this.pattern = null;
            this.params = new Object[0];
        }
    }
}

