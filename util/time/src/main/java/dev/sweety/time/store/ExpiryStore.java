package dev.sweety.time.store;

import org.jetbrains.annotations.Nullable;

/**
 * Bounded key-value store where entries expire after a time-to-live.
 *
 * <p>{@link ExpiryCache} provides a Caffeine-backed implementation for {@link Expirable} values.
 * {@link TimedExpiryCache} wraps any value type with a fixed TTL.
 */
public interface ExpiryStore<K, V> {

    /** Inserts or replaces the mapping for {@code key}. */
    void add(K key, V value);

    /** Returns the value for {@code key}, or {@code null} if absent or expired. */
    @Nullable V get(K key);

    /** Removes and returns the value for {@code key}, or {@code null} if absent or expired. */
    @Nullable V consume(K key);

    /** Removes the mapping for {@code key}. No-op if absent. */
    void remove(K key);

    /** Forces eviction of expired entries. Optional — implementations backed by Caffeine clean up asynchronously. */
    default void cleanUp() {}
}
