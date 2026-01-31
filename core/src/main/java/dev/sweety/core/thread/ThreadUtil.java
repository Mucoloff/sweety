package dev.sweety.core.thread;

import lombok.experimental.UtilityClass;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@UtilityClass
public class ThreadUtil {

    public ScheduledExecutorService namedScheduler(final String name){
        return Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, name);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler((th, ex) -> System.err.printf("Uncaught exception in %s: %s\n", th.getName(), ex));
            return t;
        });
    };

}
