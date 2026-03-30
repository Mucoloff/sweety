package dev.sweety.util.logger;

import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.profile.LogProfile;
import dev.sweety.util.logger.util.LogArguments;

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

    public LogLevel level() {
        return level;
    }

    public String loggerName() {
        return loggerName;
    }

    public LogProfile profile() {
        return profile;
    }

    public Object[] rawArgs() {
        return rawArgs;
    }

    public String pattern() {
        return pattern;
    }

    public Object[] params() {
        return params;
    }
}

