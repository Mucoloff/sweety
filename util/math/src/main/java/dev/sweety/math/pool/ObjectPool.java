package dev.sweety.math.pool;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Generic thread-safe object pool backed by a lock-free {@link ConcurrentLinkedDeque}.
 *
 * <p>Compared to the previous {@code synchronized + ArrayDeque} implementation:
 * <ul>
 *   <li>{@link #obtain()} and {@link #release} are lock-free (CAS-based).</li>
 *   <li>{@link #size()} is an O(1) {@link AtomicInteger} read instead of a lock+traverse.</li>
 *   <li>Throughput scales linearly with the number of threads rather than serialising on a
 *       single monitor.</li>
 * </ul>
 *
 * @param <T> the pooled object type
 */
public class ObjectPool<T> {

    private final ConcurrentLinkedDeque<T> pool = new ConcurrentLinkedDeque<>();
    private final AtomicInteger count = new AtomicInteger(0);
    private final Supplier<T> factory;
    private final Predicate<T> validator;
    private final int maxSize;

    public ObjectPool(Supplier<T> factory) {
        this(factory, _ -> true, Integer.MAX_VALUE);
    }

    public ObjectPool(Supplier<T> factory, int maxSize) {
        this(factory, _ -> true, maxSize);
    }

    public ObjectPool(Supplier<T> factory, Predicate<T> validator, int maxSize) {
        this.factory = factory;
        this.validator = validator;
        this.maxSize = maxSize;
    }

    /**
     * Returns a pooled instance, or creates a new one if the pool is empty.
     *
     * @return a non-null instance ready for use
     */
    public T obtain() {
        T obj = pool.pollFirst();
        if (obj != null) {
            count.decrementAndGet();
            return obj;
        }
        return factory.get();
    }

    /**
     * Borrows an object, applies {@code consume}, then automatically releases it back.
     *
     * @param <V>     return type of {@code consume}
     * @param consume function to apply to the borrowed object
     * @return the result of {@code consume}
     */
    public <V> V get(Function<T, V> consume) {
        T obj = obtain();
        V result = consume.apply(obj);
        release(obj);
        return result;
    }

    /**
     * Returns {@code obj} to the pool if it passes the validator and the pool is not full.
     * Discards the object silently if either condition is not met.
     *
     * @param obj the object to return (ignored if {@code null})
     */
    public void release(T obj) {
        if (obj == null) return;
        if (count.get() < maxSize && validator.test(obj)) {
            pool.offerFirst(obj);
            count.incrementAndGet();
        }
    }

    /**
     * Removes all pooled objects and resets the size counter.
     */
    public void clear() {
        pool.clear();
        count.set(0);
    }

    /**
     * Returns the approximate number of objects currently in the pool.
     * This is an O(1) read and does not require a lock.
     *
     * @return current pool size
     */
    public int size() {
        return count.get();
    }
}
