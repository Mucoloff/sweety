package dev.sweety.util.logger;

import dev.sweety.color.AnsiColor;
import dev.sweety.util.logger.backend.ConsoleBackend;
import dev.sweety.util.logger.backend.FileBackend;
import dev.sweety.util.logger.backend.LoggerBackend;
import dev.sweety.util.logger.level.LogLevel;
import dev.sweety.util.logger.profile.LogProfile;
import dev.sweety.util.logger.profile.ProfileScope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimpleLogger implements LogHelper {

    protected final String name;
    private final ThreadLocal<Deque<LogProfile>> profiles = ThreadLocal.withInitial(ArrayDeque::new);
    private final List<LoggerBackend> backends = new CopyOnWriteArrayList<>();

    public SimpleLogger(String name) {
        this.name = name;
        backends.add(new ConsoleBackend());
    }

    public SimpleLogger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    SimpleLogger(String name, List<LoggerBackend> backends) {
        this.name = name;
        this.backends.addAll(backends);
    }

    public static SimpleLogger of(String name) {
        return new SimpleLogger(name);
    }

    public static SimpleLogger of(Class<?> clazz) {
        return new SimpleLogger(clazz);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<LoggerBackend> backends = new ArrayList<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder backend(LoggerBackend backend) {
            backends.clear();
            backends.add(backend);
            return this;
        }

        public Builder addBackend(LoggerBackend backend) {
            backends.add(backend);
            return this;
        }

        public SimpleLogger build() {
            if (backends.isEmpty()) backends.add(new ConsoleBackend());
            return new SimpleLogger(name, backends);
        }
    }

    public static void log(LogLevel level, String name, Object... input) {
        new ConsoleBackend().log(new LogEvent(level, name, null, input));
    }

    public SimpleLogger setBackend(LoggerBackend backend) {
        backends.clear();
        backends.add(backend != null ? backend : new ConsoleBackend());
        return this;
    }

    public SimpleLogger addBackend(LoggerBackend backend) {
        backends.add(backend);
        return this;
    }

    public SimpleLogger setFileBackend(FileBackend fileBackend) {
        return addBackend(fileBackend);
    }

    public SimpleLogger log(LogLevel level, Object... input) {
        LogProfile profile = null;
        LogEvent event = null;

        for (LoggerBackend backend : backends) {
            if (!backend.isEnabled(level)) continue;
            if (event == null) {
                profile = profiles.get().peek();
                event = new LogEvent(level, name, profile, input);
            }
            backend.log(event);
        }
        return this;
    }

    public SimpleLogger push(String profile, AnsiColor color) {
        return push(profile);
    }

    public SimpleLogger push(String profile, String color) {
        return push(profile);
    }

    public SimpleLogger push(String profile) {
        final Deque<LogProfile> stack = profiles.get();
        stack.push(LogProfile.of(profile, stack.peek()));
        return this;
    }

    public SimpleLogger pop() {
        Deque<LogProfile> stack = profiles.get();
        if (!stack.isEmpty()) stack.pop();
        return this;
    }

    public String popProfile() {
        LogProfile p = profiles.get().poll();
        return p != null ? p.name() : null;
    }

    public String switchProfile(String profile) {
        final Deque<LogProfile> stack = profiles.get();
        LogProfile old = stack.poll();
        stack.push(LogProfile.of(profile, stack.peek()));
        return old != null ? old.name() : null;
    }

    public ProfileScope withProfile(String profile) {
        return new ProfileScope(push(profile));
    }

    public String name() {
        return name;
    }

    public List<LoggerBackend> backends() {
        return List.copyOf(backends);
    }

    @Override
    public SimpleLogger info(Object... input) {
        return log(LogLevel.INFO, input);
    }

    @Override
    public SimpleLogger warn(Object... input) {
        return log(LogLevel.WARN, input);
    }

    @Override
    public SimpleLogger error(Object... input) {
        return log(LogLevel.ERROR, input);
    }

    @Override
    public SimpleLogger debug(Object... input) {
        return log(LogLevel.DEBUG, input);
    }

    @Override
    public SimpleLogger trace(Object... input) {
        return log(LogLevel.TRACE, input);
    }
}
