package dev.sweety.core.thread;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileThread {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "profile-thread-" + THREAD_COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger profileCount = new AtomicInteger(0);

    public int getProfileCount() {
        return this.profileCount.get();
    }

    public void execute(Runnable runnable) {
        if (this.thread.isShutdown() || this.thread.isTerminated()) return;
        this.thread.execute(runnable);
    }

    public void executeWithDelay(Runnable runnable, long delay, TimeUnit unit) {
        if (this.thread.isShutdown() || this.thread.isTerminated()) return;
        this.thread.schedule(runnable, delay, unit);
    }

    public void executeRepeatingTask(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        if (this.thread.isShutdown() || this.thread.isTerminated()) return;
        this.thread.scheduleAtFixedRate(runnable, initialDelay, period, unit);
    }

    public ProfileThread incrementAndGet() {
        this.profileCount.incrementAndGet();
        return this;
    }

    public void decrement() {
        // evita valori negativi
        this.profileCount.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public ProfileThread shutdown() {
        this.thread.shutdownNow();
        return this;
    }


}