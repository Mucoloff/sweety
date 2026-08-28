package dev.sweety.util.logger.formatter;

import dev.sweety.exception.ExceptionUtils;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.util.LogArguments;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleLogFormatter implements LogFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String format(LogLevel level, String loggerName, Object[] args) {
        // Mirrors Minecraft's Log4j line shape — `[time] [thread/LEVEL] (Tag) msg` — so bootstrap
        // console logs sit visually inline with the loader/MC logs in the same stream.
        final String time = LocalDateTime.now().format(TIME_FORMATTER);
        final String thread = Thread.currentThread().getName();
        final String prefix = "[%s] [%s/%s] (%s)".formatted(time, thread, level, loggerName);
        final String message = LogFormatter.buildMessage(args);
        return prefix + " " + message;
    }
}
