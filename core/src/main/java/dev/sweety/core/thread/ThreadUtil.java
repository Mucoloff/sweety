package dev.sweety.core.thread;

import lombok.experimental.UtilityClass;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@UtilityClass
public class ThreadUtil {

    private ThreadFactory factory(String name) {
        return r -> {
            final Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((th, ex) -> System.err.printf("Uncaught exception in %s: %s\n", th.getName(), ex));
            return t;
        };
    }

    public ScheduledExecutorService singleThreadScheduler(final String name) {
        return Executors.newSingleThreadScheduledExecutor(factory(name));
    }

    public ScheduledExecutorService poolThreadScheduler(final int pool, final String name) {
        return Executors.newScheduledThreadPool(pool, factory(name));
    }

}
