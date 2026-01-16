package dev.sweety.core.thread;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileThread {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private final AtomicInteger profileCount = new AtomicInteger(0);
    private final ScheduledExecutorService thread;

    public ProfileThread(final String name) {
        this.thread = ThreadUtil.namedScheduler(name + "-" + THREAD_COUNTER.incrementAndGet());
    }

    public int getProfileCount() {
        return profileCount.get();
    }

    public ProfileThread incrementAndGet() {
        profileCount.incrementAndGet();
        return this;
    }

    public void decrement() {
        profileCount.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public ProfileThread shutdown() {
        thread.shutdownNow();
        return this;
    }

    // ---------------------- Utility generate ----------------------
    private <V> CompletableFuture<V> wrapFuture(final CompletableFuture<V> future, final Future<?> scheduled) {
        if (thread.isShutdown() || thread.isTerminated()) {
            future.completeExceptionally(new RejectedExecutionException("ProfileThread is shutdown or terminated."));
            if (scheduled != null) scheduled.cancel(false);
            return future;
        }

        if (scheduled != null) {
            future.whenComplete((r, t) -> {
                if (future.isCancelled()) scheduled.cancel(false);
            });
        }

        return future;
    }

    private static <T> Callable<T> fromRunnable(Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    private static <T> Runnable completeCallable(Callable<T> callable, CompletableFuture<T> future) {
        return () -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
    }

    // ---------------------- Base execute ----------------------
    public CompletableFuture<?> execute(Runnable runnable) {
        return execute(fromRunnable(runnable));
    }

    public <T> CompletableFuture<T> execute(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (thread.isShutdown() || thread.isTerminated()) {
            future.completeExceptionally(new RejectedExecutionException("ProfileThread is shutdown or terminated."));
            return future;
        }

        thread.execute(completeCallable(callable, future));

        return future;
    }

    // ---------------------- Schedule single delay ----------------------
    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        CompletableFuture<V> future = new CompletableFuture<>();
        //noinspection unchecked
        ScheduledFuture<V> scheduled = (ScheduledFuture<V>) thread.schedule(completeCallable(callable, future), delay, unit);
        return wrapFuture(future, scheduled);
    }

    public CompletableFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return schedule(fromRunnable(runnable), delay, unit);
    }

    // ---------------------- Schedule with fixed delay ----------------------

    public <V> CompletableFuture<V> scheduleWithFixedDelay(Callable<V> callable, long initialDelay, long delay, TimeUnit unit) {
        CompletableFuture<V> future = new CompletableFuture<>();
        //noinspection unchecked
        ScheduledFuture<V> scheduled = (ScheduledFuture<V>) thread.scheduleWithFixedDelay(completeCallable(callable, future), initialDelay, delay, unit);

        return wrapFuture(future, scheduled);
    }

    public CompletableFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelay(fromRunnable(runnable), initialDelay, delay, unit);
    }

    // ---------------------- Schedule at fixed rate ----------------------
    public <V> CompletableFuture<V> scheduleAtFixedRate(Callable<V> callable, long initialDelay, long period, TimeUnit unit) {
        CompletableFuture<V> future = new CompletableFuture<>();
        //noinspection unchecked
        ScheduledFuture<V> scheduled = (ScheduledFuture<V>) thread.scheduleAtFixedRate(completeCallable(callable, future), initialDelay, period, unit);

        return wrapFuture(future, scheduled);
    }

    public CompletableFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(fromRunnable(runnable), initialDelay, period, unit);
    }
}
