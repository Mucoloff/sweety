package dev.sweety.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AsyncScheduler {

    private static final Logger LOG = Logger.getLogger(AsyncScheduler.class.getName());

    private static final ScheduledExecutorService scheduler =
            ThreadUtil.poolThreadScheduler(30, "MX Thread");

    private AsyncScheduler() {}

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public static void shutdown() {
        scheduler.shutdownNow();
    }

    public static Future<?> run(Runnable runnable) {
        return scheduler.submit(wrap(runnable));
    }

    public static <T> Future<T> run(Callable<T> callable) {
        return scheduler.submit(wrap(callable));
    }

    public static ScheduledFuture<?> later(Runnable runnable, long delay, TimeUnit time) {
        return scheduler.schedule(wrap(runnable), delay, time);
    }

    public static ScheduledFuture<?> timer(Runnable runnable, long delay, long period, TimeUnit time) {
        return scheduler.scheduleAtFixedRate(wrap(runnable), delay, period, time);
    }

    public static void cancel(ScheduledFuture<?> timer) {
        if (timer != null) timer.cancel(true);
    }

    // ── wrappers that log uncaught exceptions ─────────────────────────────────

    private static Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, "Uncaught exception in async task", e);
            }
        };
    }

    private static <T> Callable<T> wrap(Callable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, "Uncaught exception in async task", e);
                throw e;
            }
        };
    }
}
