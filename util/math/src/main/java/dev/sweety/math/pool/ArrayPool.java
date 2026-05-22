package dev.sweety.math.pool;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Pooling interface for reusable arrays of any type.
 *
 * <p>Two implementations are available via the factory methods:
 * <ul>
 *   <li>{@link #threadLocal} — per-thread ArrayDeque, zero contention.
 *   <li>{@link #shared} — ConcurrentLinkedDeque, safe for cross-thread use.
 *       Fixes the peek/pollFirst TOCTOU present in the previous implementation:
 *       {@code pollFirst()} is called directly; if the taken array is too small it is
 *       dropped (not returned to the pool) and a fresh one is allocated.
 * </ul>
 *
 * <p>Both variants accept any array type via {@code IntFunction<T>} (factory) and
 * {@code ToIntFunction<T>} (length extractor). Convenience factories for {@code byte[]},
 * {@code int[]}, and {@code float[]} are provided.
 */
public interface ArrayPool<T> {

    /**
     * Returns an array whose length is {@code >= minSize}.
     * The returned array may be larger than requested.
     */
    @Acquire
    T acquire(int minSize);

    /** Returns {@code arr} to the pool if it is within the acceptable size range. */
    @Release
    void release(T arr);

    // ========================== TYPED CONVENIENCE FACTORIES ==========================

    static ArrayPool<byte[]> threadLocalBytes(int defaultSize, int maxPerThread) {
        return threadLocal(byte[]::new, a -> a.length, defaultSize, maxPerThread);
    }

    static ArrayPool<int[]> threadLocalInts(int defaultSize, int maxPerThread) {
        return threadLocal(int[]::new, a -> a.length, defaultSize, maxPerThread);
    }

    static ArrayPool<long[]> threadLocalLongs(int defaultSize, int maxPerThread) {
        return threadLocal(long[]::new, a -> a.length, defaultSize, maxPerThread);
    }

    static ArrayPool<float[]> threadLocalFloats(int defaultSize, int maxPerThread) {
        return threadLocal(float[]::new, a -> a.length, defaultSize, maxPerThread);
    }

    static ArrayPool<double[]> threadLocalDoubles(int defaultSize, int maxPerThread) {
        return threadLocal(double[]::new, a -> a.length, defaultSize, maxPerThread);
    }

    static ArrayPool<byte[]> sharedBytes(int defaultSize, int maxPoolSize) {
        return shared(byte[]::new, a -> a.length, defaultSize, maxPoolSize);
    }

    static ArrayPool<int[]> sharedInts(int defaultSize, int maxPoolSize) {
        return shared(int[]::new, a -> a.length, defaultSize, maxPoolSize);
    }

    static ArrayPool<long[]> sharedLongs(int defaultSize, int maxPoolSize) {
        return shared(long[]::new, a -> a.length, defaultSize, maxPoolSize);
    }

    static ArrayPool<float[]> sharedFloats(int defaultSize, int maxPoolSize) {
        return shared(float[]::new, a -> a.length, defaultSize, maxPoolSize);
    }

    static ArrayPool<double[]> sharedDoubles(int defaultSize, int maxPoolSize) {
        return shared(double[]::new, a -> a.length, defaultSize, maxPoolSize);
    }

    // ========================== GENERIC FACTORIES ==========================

    static <T> ArrayPool<T> threadLocal(IntFunction<T> factory, ToIntFunction<T> length,
                                        int defaultSize, Consumer<T> onDiscard, int maxPerThread) {
        return new ThreadLocalImpl<>(factory, length, defaultSize, onDiscard, maxPerThread);
    }

    static <T> ArrayPool<T> threadLocal(IntFunction<T> factory, ToIntFunction<T> length,
                                        int defaultSize, int maxPerThread) {
        return threadLocal(factory, length, defaultSize, _ -> {
        }, maxPerThread);
    }

    static <T> ArrayPool<T> shared(IntFunction<T> factory, ToIntFunction<T> length,
                                   int defaultSize, int maxPoolSize) {
        return new SharedImpl<>(factory, length, defaultSize, maxPoolSize);
    }

    // ========================== IMPLEMENTATIONS ==========================

    final class ThreadLocalImpl<T> implements ArrayPool<T> {
        private final ThreadLocal<ArrayDeque<T>> pool = ThreadLocal.withInitial(ArrayDeque::new);
        private final IntFunction<T> factory;
        private final ToIntFunction<T> length;
        private final int defaultSize;
        private final Consumer<T> onDiscard;
        private final int maxPerThread;

        ThreadLocalImpl(IntFunction<T> factory, ToIntFunction<T> length,
                        int defaultSize, Consumer<T> onDiscard, int maxPerThread) {
            this.factory = factory;
            this.length = length;
            this.defaultSize = defaultSize;
            this.onDiscard = onDiscard;
            this.maxPerThread = maxPerThread;
        }

        @Override
        public T acquire(int minSize) {
            ArrayDeque<T> deque = pool.get();
            T arr = deque.poll();
            if (arr != null && length.applyAsInt(arr) >= minSize) return arr;
            return factory.apply(Math.max(defaultSize, minSize));
        }

        @Override
        public void release(T arr) {
            if (arr == null) return;
            int len = length.applyAsInt(arr);
            if (len < defaultSize / 2 || len > defaultSize * 2) {
                onDiscard.accept(arr);
                return;
            }
            ArrayDeque<T> deque = pool.get();
            if (deque.size() < maxPerThread) deque.push(arr);
            else onDiscard.accept(arr);
        }
    }

    final class SharedImpl<T> implements ArrayPool<T> {
        private final ConcurrentLinkedDeque<T> pool = new ConcurrentLinkedDeque<>();
        private final AtomicInteger count = new AtomicInteger();
        private final IntFunction<T> factory;
        private final ToIntFunction<T> length;
        private final int defaultSize;
        private final int maxPoolSize;

        SharedImpl(IntFunction<T> factory, ToIntFunction<T> length,
                   int defaultSize, int maxPoolSize) {
            this.factory = factory;
            this.length = length;
            this.defaultSize = defaultSize;
            this.maxPoolSize = maxPoolSize;
        }

        @Override
        public T acquire(int minSize) {
            // pollFirst directly — no peek/poll TOCTOU
            T arr = pool.pollFirst();
            if (arr != null) {
                count.decrementAndGet();
                if (length.applyAsInt(arr) >= minSize) return arr;
                // too small — drop it, allocate fresh
            }
            return factory.apply(Math.max(defaultSize, minSize));
        }

        @Override
        public void release(T arr) {
            if (arr == null) return;
            int len = length.applyAsInt(arr);
            if (count.get() < maxPoolSize && len >= defaultSize / 2 && len <= defaultSize * 2) {
                pool.offerFirst(arr);
                count.incrementAndGet();
            }
        }
    }
}
