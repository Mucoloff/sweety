package dev.sweety.util.logger.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.util.logger.LogEvent;
import dev.sweety.util.logger.formatter.LogFormatter;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.formatter.SimpleLogFormatter;

// Default console backend (stdout/stderr)
public class ConsoleBackend implements LoggerBackend {
    
    private final LogFormatter formatter;
    private LogLevel minLevel = LogLevel.INFO; // Default minimum level

    public ConsoleBackend() {
        this(new SimpleLogFormatter());
    }

    public ConsoleBackend(LogFormatter formatter) {
        this.formatter = formatter;
    }

    public ConsoleBackend setMinLevel(LogLevel minLevel) {
        this.minLevel = minLevel;
        return this;
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return getSeverity(level) >= getSeverity(minLevel);
    }
    
    private int getSeverity(LogLevel level) {
        return switch (level) {
            case ERROR -> 50;
            case WARN -> 40;
            case INFO -> 30;
            case DEBUG -> 20;
            case TRACE -> 10;
        };
    }

    @Override
    public void log(LogEvent event) {
        String formatted = formatter.format(event.getLevel(), event.getLoggerName(), event.getProfile(), event.getRawArgs());
        String color = getColor(event.getLevel());
        
        // Add color: Color + Message + Reset
        String line = color + formatted + AnsiColor.RESET.getColor();

        if (event.getLevel() == LogLevel.ERROR) System.err.println(line);
        else System.out.println(line);
    }
    
    private String getColor(LogLevel level) {
        return switch (level) {
            case INFO -> AnsiColor.WHITE_BRIGHT.getColor();
            case WARN -> AnsiColor.YELLOW_BRIGHT.getColor();
            case ERROR -> AnsiColor.RED_BRIGHT.getColor();
            case DEBUG -> AnsiColor.PURPLE_BRIGHT.getColor();
            case TRACE -> AnsiColor.RESET.getColor();
        };
    }
}
