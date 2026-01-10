package dev.sweety.core.logger;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.exception.ExceptionUtils;
import dev.sweety.core.logger.backend.ConsoleBackend;
import dev.sweety.core.logger.backend.LoggerBackend;
import dev.sweety.core.logger.profile.ProfileScope;
import dev.sweety.core.math.vector.stack.LinkedStack;
import dev.sweety.core.math.vector.stack.Stack;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.function.BiConsumer;

public class SimpleLogger implements LogHelper {
    protected final String name;

    private final ThreadLocal<Stack<String>> profiles = ThreadLocal.withInitial(LinkedStack::new);
    // Pluggable backend support
    @Getter
    private volatile LoggerBackend backend = new ConsoleBackend();

    public SimpleLogger fallback() {
        return this;
    }

    public SimpleLogger(String name) {
        this.name = name;
    }

    public SimpleLogger(Class<?> clazz) {
        this.name = clazz.getSimpleName();
    }

    // Allow setting a custom backend (e.g., SLF4J, Log4j, java.util.logging, etc.)
    public SimpleLogger setBackend(LoggerBackend backend) {
        this.backend = (backend != null) ? backend : new ConsoleBackend();
        return this;
    }

    public static void log(LogLevel level, BiConsumer<LogLevel, String> logger, Object... input) {
        final String color = level.color().getColor();
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();

        logger.accept(level, color + message);
    }

    public static void log(LogLevel level, String name, Object... input) {
        final String color = level.color().getColor();
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();

        System.out.printf("%s(!)%s [%s] %s\n", AnsiColor.RED.getColor(), color, name, message);
    }

    public void log(LogLevel level, Object... input) {
        final String prefix = formatPrefix(level);
        final String color = level.color().getColor();
        final String message = parseMessage(input) + AnsiColor.RESET.getColor();
        final String line = color + prefix + " " + message;

        backend.log(level, name, profiles.get().top(), line);
    }

    private String formatPrefix(LogLevel level) {
        final String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        final String top = profiles.get().top();
        final String suffix = (top != null && !top.isEmpty()) ? ("@" + top) : "";
        return "[" + time + "][" + level + "][" + name + suffix + "]";
    }

    // Profile management (thread-local) with hierarchical composition
    public void pushProfile(String profile) {
        final Stack<String> stack = profiles.get();
        final String suffix = (stack.top() != null && !stack.top().isEmpty()) ? (stack.top() + "@") : "";
        stack.push(suffix + profile);
    }

    public String popProfile() {
        return profiles.get().pop();
    }

    public String switchProfile(String profile) {
        final Stack<String> stack = profiles.get();
        String old = stack.pop();
        stack.push(profile);
        return old;
    }

    public ProfileScope withProfile(String profile) {
        pushProfile(profile);
        return new ProfileScope(this);
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
        log(LogLevel.INFO, input);
    }

    public void warn(Object... input) {
        log(LogLevel.WARN, input);
    }

    public void error(Object... input) {
        log(LogLevel.ERROR, input);
    }

    public void debug(Object... input) {
        log(LogLevel.DEBUG, input);
    }

    public void trace(Object... input) {
        log(LogLevel.TRACE, input);
    }

}
