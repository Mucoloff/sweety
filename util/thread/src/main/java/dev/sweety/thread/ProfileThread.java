package dev.sweety.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileThread {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final AtomicInteger profileCount = new AtomicInteger(0);
    private final ExecutorService thread;

    public ProfileThread(final String name, final ThreadType type) {
        String indexedName = name + "-" + THREAD_COUNTER.incrementAndGet();
        this.thread = switch (type) {
            case SINGLE  -> ThreadUtil.singleThreadScheduler(indexedName);
            case FIXED   -> ThreadUtil.fixedThreadPool(Runtime.getRuntime().availableProcessors(), indexedName);
            case CACHED  -> ThreadUtil.cachedThreadPool(indexedName);
            case VIRTUAL -> ThreadUtil.virtualThreadExecutor(indexedName);
            case POOL    -> ThreadUtil.poolThreadScheduler(Runtime.getRuntime().availableProcessors(), indexedName);
        };
    }

    public ProfileThread(final String name) {
        this(name, ThreadType.SINGLE);
    }

    public int getProfileCount() {
        return profileCount.get();
    }

    public ProfileThread incrementAndGet() {
        profileCount.incrementAndGet();
        return this;
    }

    public int decrement() {
        return profileCount.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public ProfileThread shutdown() {
        thread.shutdownNow();
        return this;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static <T> Callable<T> fromRunnable(Runnable runnable) {
        return () -> { runnable.run(); return null; };
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

    // ── execute ───────────────────────────────────────────────────────────────

    public CompletableFuture<?> execute(Runnable runnable) {
        return execute(fromRunnable(runnable));
    }

    public <T> CompletableFuture<T> execute(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            thread.execute(completeCallable(callable, future));
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // ── schedule (single delay) ───────────────────────────────────────────────

    public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledExecutorService scheduler = requireScheduler();
        CompletableFuture<V> future = new CompletableFuture<>();
        try {
            //noinspection unchecked
            ScheduledFuture<V> scheduled = (ScheduledFuture<V>) scheduler.schedule(
                    completeCallable(callable, future), delay, unit);
            cancelOnFutureCancel(future, scheduled);
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return schedule(fromRunnable(runnable), delay, unit);
    }

    // ── scheduleWithFixedDelay ────────────────────────────────────────────────

    public <V> CompletableFuture<V> scheduleWithFixedDelay(Callable<V> callable,
                                                            long initialDelay, long delay, TimeUnit unit) {
        ScheduledExecutorService scheduler = requireScheduler();
        CompletableFuture<V> future = new CompletableFuture<>();
        try {
            //noinspection unchecked
            ScheduledFuture<V> scheduled = (ScheduledFuture<V>) scheduler.scheduleWithFixedDelay(
                    completeCallable(callable, future), initialDelay, delay, unit);
            cancelOnFutureCancel(future, scheduled);
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<?> scheduleWithFixedDelay(Runnable runnable,
                                                        long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelay(fromRunnable(runnable), initialDelay, delay, unit);
    }

    // ── scheduleAtFixedRate ───────────────────────────────────────────────────

    public <V> CompletableFuture<V> scheduleAtFixedRate(Callable<V> callable,
                                                         long initialDelay, long period, TimeUnit unit) {
        ScheduledExecutorService scheduler = requireScheduler();
        CompletableFuture<V> future = new CompletableFuture<>();
        try {
            //noinspection unchecked
            ScheduledFuture<V> scheduled = (ScheduledFuture<V>) scheduler.scheduleAtFixedRate(
                    completeCallable(callable, future), initialDelay, period, unit);
            cancelOnFutureCancel(future, scheduled);
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<?> scheduleAtFixedRate(Runnable runnable,
                                                     long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(fromRunnable(runnable), initialDelay, period, unit);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private ScheduledExecutorService requireScheduler() {
        if (!(thread instanceof ScheduledExecutorService s))
            throw new UnsupportedOperationException("ProfileThread type does not support scheduling.");
        return s;
    }

    private static void cancelOnFutureCancel(CompletableFuture<?> future, ScheduledFuture<?> scheduled) {
        future.whenComplete((r, t) -> {
            if (future.isCancelled()) scheduled.cancel(false);
        });
    }
}
