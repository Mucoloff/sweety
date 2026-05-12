package dev.sweety.math.pool;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Specialized pool for arrays with size requirements, backed by a lock-free
 * {@link ConcurrentLinkedDeque}.
 *
 * <p>Works with any array type ({@code float[]}, {@code int[]}, {@code Object[]}, etc.).
 * Only the top-of-deque entry is inspected for size eligibility ({@link #obtain(int)});
 * if it is too small a new array is allocated rather than scanning the whole pool.
 *
 * @param <T> the array type (e.g. {@code int[]}, {@code Object[]})
 */
public class ArrayPool<T> {

    private final ConcurrentLinkedDeque<T> pool = new ConcurrentLinkedDeque<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final IntFunction<T> factory;
    private final ToIntFunction<T> lengthExtractor;
    private final int defaultSize;
    private final int maxPoolSize;

    public ArrayPool(IntFunction<T> factory, ToIntFunction<T> lengthExtractor,
                     int defaultSize, int maxPoolSize) {
        this.factory = factory;
        this.lengthExtractor = lengthExtractor;
        this.defaultSize = defaultSize;
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * Returns a pooled array whose length is {@code >= minSize}, or allocates a new one.
     * Only the most-recently-used (head) array is checked; if it is too small a fresh
     * allocation is returned and the pooled entry is left for a future caller.
     *
     * @param minSize minimum required array length
     * @return a non-null array of length {@code >= minSize}
     */
    public T obtain(int minSize) {
        T top = pool.peekFirst();
        if (top != null && lengthExtractor.applyAsInt(top) >= minSize) {
            T taken = pool.pollFirst();
            if (taken != null) {
                count.decrementAndGet();
                return taken;
            }
        }
        return factory.apply(Math.max(defaultSize, minSize));
    }

    /**
     * Returns {@code array} to the pool if its length is within the acceptable range
     * ({@code [defaultSize/2, defaultSize*2]}) and the pool is not full.
     *
     * @param array the array to return (ignored if {@code null})
     */
    public void release(T array) {
        if (array == null) return;
        int length = lengthExtractor.applyAsInt(array);
        if (count.get() < maxPoolSize && length >= defaultSize / 2 && length <= defaultSize * 2) {
            pool.offerFirst(array);
            count.incrementAndGet();
        }
    }

    /**
     * Removes all pooled arrays and resets the size counter.
     */
    public void clear() {
        pool.clear();
        count.set(0);
    }

    /**
     * Returns the approximate number of arrays currently in the pool.
     * This is an O(1) read.
     *
     * @return current pool size
     */
    public int size() {
        return count.get();
    }
}
