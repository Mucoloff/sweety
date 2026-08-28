package dev.sweety.thread;

import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class ThreadUtil {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ThreadUtil.class);

    public static ThreadFactory factory(String name) {
        return r -> {
            final Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            // Every ThreadUtil-created thread in the project routes through here — was printf("%s", ex)
            // (toString only, no stack trace) straight to stderr, the one place across the whole
            // codebase where an uncaught exception's actual trace was thrown away.
            t.setUncaughtExceptionHandler((th, ex) -> LOGGER.error("Uncaught exception in {}", th.getName(), ex));
            return t;
        };
    }

    public static ScheduledExecutorService singleThreadScheduler(final String name) {
        return Executors.newSingleThreadScheduledExecutor(factory(name));
    }

    public static ScheduledExecutorService poolThreadScheduler(final int pool, final String name) {
        return Executors.newScheduledThreadPool(pool, factory(name));
    }

    public static ExecutorService cachedThreadPool(final String name) {
        return Executors.newCachedThreadPool(factory(name));
    }

    public static ExecutorService fixedThreadPool(final int pool, final String name) {
        return Executors.newFixedThreadPool(pool, factory(name));
    }

    public static ExecutorService virtualThreadExecutor(final String name) {
        ThreadFactory factory = Thread.ofVirtual().name(name).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

}