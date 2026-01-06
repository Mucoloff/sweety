package dev.sweety.core.logger;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.exception.ExceptionUtils;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Collection;
import java.util.StringJoiner;

public class SimpleLogger implements LogHelper {
    protected final String name;
    private final Logger logger;

    @Setter
    private boolean useFallback = false;

    public SimpleLogger fallback() {
        this.useFallback = true;
        return this;
    }

    public SimpleLogger(String name) {
        this.logger = LoggerFactory.getLogger(this.name = name);
    }

    public SimpleLogger(Class<?> clazz) {
        this.name = clazz.getSimpleName();
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static void log(Level level, Logger logger, Object... input) {
        final String color = getColor(level);
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();

        logger.makeLoggingEventBuilder(level).log(color + message);
    }

    public static void log(Level level, String name, Object... input) {
        final String color = getColor(level);
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();

        System.out.printf("%s(!)%s [%s] %s\n", AnsiColor.RED.getColor(), color, name, message);
    }

    public void log(Level level, Object... input) {
        //String message = "%s[%s] %s: %s".formatted(time(), level, name, );

        final String color = getColor(level);
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();

        this.logger.makeLoggingEventBuilder(level).log(color + message);

        if (this.useFallback) {
            System.out.printf("%s(!)%s [%s] %s\n", AnsiColor.RED.getColor(), color, this.name, message);
        }
    }

    @Override
    public String getMessage(Object[] input) {
        return parseMessage(input);
    }

    private static String parseMessage(Object[] input) {
        final StringJoiner joiner = new StringJoiner(" ");
        for (Object part : input) {

            joiner.add(switch (part) {
                case null -> "<null>";
                case String s -> s;
                case AnsiColor color -> color.getColor();
                case Class<?> clazz -> clazz.getSimpleName();
                case Throwable e -> ExceptionUtils.getStackTrace(e);
                case Object[] arr -> parseMessage(arr);
                /*
                case List<?> l -> getMessage(l.toArray());
                case Set<?> s -> getMessage(s.toArray());
                -- handled by Collection<?>
                */
                case Collection<?> c -> parseMessage(c.toArray());
                default -> part.toString();
            });

        }
        return joiner.toString();
    }

    public void info(Object... input) {
        log(Level.INFO, input);
    }

    public void warn(Object... input) {
        log(Level.WARN, input);
    }

    public void error(Object... input) {
        log(Level.ERROR, input);
    }

    public void debug(Object... input) {
        log(Level.DEBUG, input);
    }

    public void trace(Object... input) {
        log(Level.TRACE, input);
    }

    private static String getColor(Level level) {
        return (switch (level) {
            case INFO -> AnsiColor.WHITE_BRIGHT;
            case WARN -> AnsiColor.YELLOW_BRIGHT;
            case ERROR -> AnsiColor.RED_BRIGHT;
            case DEBUG -> AnsiColor.PURPLE_BRIGHT;
            case TRACE -> AnsiColor.RESET;
        }).getColor();
    }

}
