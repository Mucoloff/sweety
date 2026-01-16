package dev.sweety.core.thread;

import lombok.experimental.UtilityClass;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@UtilityClass
public class ThreadUtil {

    public ScheduledExecutorService namedScheduler(final String name){
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    };

}
