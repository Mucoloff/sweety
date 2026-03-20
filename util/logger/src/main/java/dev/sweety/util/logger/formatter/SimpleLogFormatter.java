package dev.sweety.util.logger.formatter;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.exception.ExceptionUtils;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.profile.LogProfile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.StringJoiner;

public class SimpleLogFormatter implements LogFormatter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String format(LogLevel level, String loggerName, LogProfile profile, Object[] args) {
        final String time = LocalDateTime.now().format(TIME_FORMATTER);
        final String suffix = (profile != null) ? ("@" + profile.getFullPath()) : "";
        final String prefix = "[" + time + "][" + level + "][" + loggerName + suffix + "]";
        final String message = parseMessage(args);
        return prefix + " " + message;
    }

    private String parseMessage(Object... input) {
        final StringJoiner joiner = new StringJoiner(" ");
        for (Object part : input) {
            joiner.add(switch (part) {
                case null -> "<null>";
                case String s -> s;
                case AnsiColor color -> color.getColor();
                case Class<?> clazz -> clazz.getSimpleName();
                case Throwable e -> ExceptionUtils.getStackTrace(e);
                case Object[] arr -> parseMessage(arr);
                case Collection<?> c -> parseMessage(c.toArray());
                default -> part.toString();
            });
        }
        return joiner.toString();
    }
}


