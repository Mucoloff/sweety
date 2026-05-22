package dev.sweety.math.pool;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pooling interface for reusable objects.
 *
 * <p>Two implementations are available via the factory methods:
 * <ul>
 *   <li>{@link #threadLocal} — per-thread ArrayDeque, zero contention, same-thread release contract.
 *   <li>{@link #shared} — ConcurrentLinkedDeque, safe for cross-thread acquire/release.
 * </ul>
 *
 * <p>Inspired by {@code io.netty.util.Recycler}: each implementation maps to Recycler's
 * ThreadLocal (per-thread batch) and shared (MPMC queue) modes respectively.
 */
public interface ObjectPool<T> {

    /** Acquires an object from the pool, creating a new one if the pool is empty. */
    T acquire();

    /** Returns {@code obj} to the pool. Silently ignored if null or pool is full. */
    void release(T obj);

    /** Borrows an object, applies {@code fn}, returns it, and yields the result. */
    default <V> V use(Function<T, V> fn) {
        T obj = acquire();
        try {
            return fn.apply(obj);
        } finally {
            release(obj);
        }
    }

    // ========================== FACTORIES ==========================

    /**
     * Per-thread pool. No synchronization — zero overhead for same-thread alloc/release.
     * Do NOT release from a different thread than the one that acquired.
     */
    static <T> ObjectPool<T> threadLocal(Supplier<T> factory, Consumer<T> reset,
                                         Consumer<T> onDiscard, int maxPerThread) {
        return new ThreadLocalImpl<>(factory, reset, onDiscard, maxPerThread);
    }

    static <T> ObjectPool<T> threadLocal(Supplier<T> factory, Consumer<T> reset, int maxPerThread) {
        return threadLocal(factory, reset, _ -> {}, maxPerThread);
    }

    static <T> ObjectPool<T> threadLocal(Supplier<T> factory, int maxPerThread) {
        return threadLocal(factory, _ -> {}, _ -> {}, maxPerThread);
    }

    /**
     * Shared pool, safe for concurrent acquire/release across threads.
     * Uses a lock-free ConcurrentLinkedDeque.
     */
    static <T> ObjectPool<T> shared(Supplier<T> factory, Consumer<T> reset, int maxSize) {
        return new SharedImpl<>(factory, reset, maxSize);
    }

    static <T> ObjectPool<T> shared(Supplier<T> factory, int maxSize) {
        return shared(factory, _ -> {}, maxSize);
    }

    // ========================== IMPLEMENTATIONS ==========================

    final class ThreadLocalImpl<T> implements ObjectPool<T> {
        private final ThreadLocal<ArrayDeque<T>> pool = ThreadLocal.withInitial(ArrayDeque::new);
        private final Supplier<T> factory;
        private final Consumer<T> reset;
        private final Consumer<T> onDiscard;
        private final int maxPerThread;

        ThreadLocalImpl(Supplier<T> factory, Consumer<T> reset, Consumer<T> onDiscard, int maxPerThread) {
            this.factory = factory;
            this.reset = reset;
            this.onDiscard = onDiscard;
            this.maxPerThread = maxPerThread;
        }

        @Override
        public T acquire() {
            T obj = pool.get().poll();
            return obj != null ? obj : factory.get();
        }

        @Override
        public void release(T obj) {
            if (obj == null) return;
            ArrayDeque<T> deque = pool.get();
            if (deque.size() < maxPerThread) {
                reset.accept(obj);
                deque.push(obj);
            } else {
                onDiscard.accept(obj);
            }
        }
    }

    final class SharedImpl<T> implements ObjectPool<T> {
        private final ConcurrentLinkedDeque<T> pool = new ConcurrentLinkedDeque<>();
        private final AtomicInteger count = new AtomicInteger();
        private final Supplier<T> factory;
        private final Consumer<T> reset;
        private final int maxSize;

        SharedImpl(Supplier<T> factory, Consumer<T> reset, int maxSize) {
            this.factory = factory;
            this.reset = reset;
            this.maxSize = maxSize;
        }

        @Override
        public T acquire() {
            T obj = pool.pollFirst();
            if (obj != null) {
                count.decrementAndGet();
                return obj;
            }
            return factory.get();
        }

        @Override
        public void release(T obj) {
            if (obj == null) return;
            if (count.get() < maxSize) {
                reset.accept(obj);
                pool.offerFirst(obj);
                count.incrementAndGet();
            }
        }
    }
}
