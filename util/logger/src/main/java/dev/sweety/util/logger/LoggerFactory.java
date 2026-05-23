package dev.sweety.util.logger;

import dev.sweety.util.logger.backend.ConsoleBackend;
import dev.sweety.util.logger.backend.LoggerBackend;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central factory for named {@link SimpleLogger} instances.
 *
 * <p>Loggers are cached by name; {@link #setGlobalBackend} only affects
 * loggers created after the call.
 */
public final class LoggerFactory {

    private static final ConcurrentHashMap<String, SimpleLogger> REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicReference<LoggerBackend> globalBackend =
            new AtomicReference<>(new ConsoleBackend());

    private LoggerFactory() {}

    public static void setGlobalBackend(LoggerBackend backend) {
        globalBackend.set(Objects.requireNonNull(backend, "backend"));
    }

    public static SimpleLogger getLogger(String name) {
        return REGISTRY.computeIfAbsent(name, n ->
                SimpleLogger.builder(n).backend(globalBackend.get()).build());
    }

    public static SimpleLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }

    /** Removes all cached loggers (e.g. after reconfiguring the global backend). */
    public static void reset() {
        REGISTRY.clear();
    }
}
