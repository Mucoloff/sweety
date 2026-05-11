package dev.sweety.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class ThreadUtil {

    public static ThreadFactory factory(String name) {
        return r -> {
            final Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((th, ex) -> System.err.printf("Uncaught exception in %s: %s\n", th.getName(), ex));
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
        try {
            // Using reflection to support Java 21+ while remaining compatible with older versions if necessary
            // Although the project seems to be on a high version (SourceVersion.RELEASE_24)
            return (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Exception e) {
            return cachedThreadPool(name);
        }
    }

}