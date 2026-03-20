package dev.sweety.logger;

import dev.sweety.logger.backend.ConsoleBackend;
import dev.sweety.logger.backend.FileBackend;
import dev.sweety.logger.backend.LoggerBackend;
import dev.sweety.logger.formatter.SimpleLogFormatter;
import dev.sweety.logger.level.LogLevel;
import dev.sweety.logger.profile.LogProfile;
import dev.sweety.logger.profile.ProfileScope;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SimpleLogger implements LogHelper {

    protected final String name;
    private final ThreadLocal<Deque<LogProfile>> profiles = ThreadLocal.withInitial(ArrayDeque::new);

    // Pluggable backend support
    @Getter
    private volatile LoggerBackend backend = new ConsoleBackend();
    private volatile FileBackend fileBackend;

    public SimpleLogger(String name) {
        this.name = name;
    }

    public SimpleLogger(Class<?> clazz) {
        this.name = clazz.getSimpleName();
    }

    // Allow setting a custom backend (e.g., SLF4J, Log4j, java.util.logging, etc.)
    public SimpleLogger setBackend(final LoggerBackend backend) {
        return setBackend(ignored -> backend);
    }

    public SimpleLogger setBackend(Function<String, LoggerBackend> backend) {
        this.backend = (backend != null) ? backend.apply(this.name) : new ConsoleBackend();
        return this;
    }

    public SimpleLogger setFileBackend(FileBackend fileBackend) {
        this.fileBackend = fileBackend;
        return this;
    }

    public static void log(LogLevel level, BiConsumer<LogLevel, String> logger, Object... input) {
        // Legacy support: Use temporary formatter
        String msg = new SimpleLogFormatter().format(level, "STATIC", null, input);
        logger.accept(level, msg);
    }

    public static void log(LogLevel level, String name, Object... input) {
        // Legacy support: direct console write
        new ConsoleBackend().log(new LogEvent(level, name, null, input));
    }

    public SimpleLogger log(LogLevel level, Object... input) {
        if (!backend.isEnabled(level)) {
            return this;
        }

        LogProfile profile = profiles.get().peek();
        LogEvent event = new LogEvent(level, name, profile, input);
        
        backend.log(event);
        
        if (fileBackend != null) {
            fileBackend.log(event);
        }
        
        return this;
    }

    // Deprecated internal helper if external calls relied on it, simplified to simple join
    // But since we removed it from usage, we can probably remove it.
    // Keeping public API 'push', 'pop' mostly unchanged but fixing types.

    public SimpleLogger push(String profile, String color) {
        // Colors no longer supported in profile key directly as logic is stripped
        // Just push the profile name
        return push(profile); 
    }

    public SimpleLogger push(String profile, dev.sweety.core.color.AnsiColor color) {
        return push(profile);
    }

    // Profile management (thread-local) with hierarchical composition
    public SimpleLogger push(String profile) {
        final Deque<LogProfile> stack = profiles.get();
        final LogProfile current = stack.peek();
        
        final LogProfile newProfile = LogProfile.of(profile, current);
        stack.push(newProfile);
        return this;
    }

    public SimpleLogger pop() {
        Deque<LogProfile> stack = profiles.get();
        if (!stack.isEmpty()) stack.pop();
        return this;
    }

    public String popProfile() {
        Deque<LogProfile> stack = profiles.get();
        LogProfile p = stack.poll(); // poll() returns null if empty, pop() throws
        return p != null ? p.getName() : null;
    }

    public String switchProfile(String profile) {
        final Deque<LogProfile> stack = profiles.get();
        
        // Safe pop
        LogProfile old = stack.poll(); 
        
        LogProfile parent = stack.peek();
        // If stack was empty (old == null), parent is null, which is valid for root profile
        stack.push(LogProfile.of(profile, parent));
        
        return old != null ? old.getName() : null;
    }

    public ProfileScope withProfile(String profile) {
        push(profile);
        return new ProfileScope(this);
    }

    public String name() {
        return name;
    }

    public SimpleLogger info(Object... input) {
        return log(LogLevel.INFO, input);
    }

    public SimpleLogger warn(Object... input) {
        return log(LogLevel.WARN, input);
    }

    public SimpleLogger error(Object... input) {
        return log(LogLevel.ERROR, input);
    }

    public SimpleLogger debug(Object... input) {
        return log(LogLevel.DEBUG, input);
    }

    @Override
    public SimpleLogger trace(Object... input) {
        return log(LogLevel.TRACE, input);
    }
}
