package dev.sweety.util.logger;

import dev.sweety.util.logger.backend.ConsoleBackend;
import dev.sweety.util.logger.backend.FileBackend;
import dev.sweety.util.logger.backend.LoggerBackend;
import dev.sweety.util.logger.level.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimpleLogger implements LogHelper {

    protected final String name;
    private final CopyOnWriteArrayList<LoggerBackend> backends;
    private final Map<String, SimpleLogger> children = new ConcurrentHashMap<>();

    private SimpleLogger(String name) {
        this.name = name;
        this.backends = new CopyOnWriteArrayList<>();
        backends.add(new ConsoleBackend());
    }

    private SimpleLogger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    /** Builder ctor — copies the provided list (isolated from builder config). */
    private SimpleLogger(String name, List<LoggerBackend> backends) {
        this.name = name;
        this.backends = new CopyOnWriteArrayList<>(backends);
    }

    /** Child ctor — shares the parent's live backend list so config propagates. */
    private SimpleLogger(String name, CopyOnWriteArrayList<LoggerBackend> sharedBackends) {
        this.name = name;
        this.backends = sharedBackends;
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
    public static Builder builder(Class<?> clazz) {
        return new Builder(clazz.getSimpleName());
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

    /**
     * Returns a child logger whose name is {@code this.name + "/" + profile}.
     * The child shares this logger's backend list — backend/level changes on the
     * parent are immediately visible to all children.
     */
    public SimpleLogger profile(String profile) {
        return children.computeIfAbsent(profile,
                p -> new SimpleLogger(this.name + "/" + p, this.backends));
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
        LogEvent event = null;

        for (LoggerBackend backend : backends) {
            if (!backend.isEnabled(level)) continue;
            if (event == null) event = new LogEvent(level, name, input);
            backend.log(event);
        }
        return this;
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
