package dev.sweety.time.store;

import dev.sweety.time.Expirable;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

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

    /**
     * Returns a Caffeine-backed store for {@link Expirable} values.
     * Expiry is driven by each value's own {@link Expirable#expireAt()} deadline.
     */
    static <K, V extends Expirable> ExpiryCache<K, V> of(int maxSize) {
        return new ExpiryCache<>(maxSize);
    }

    /**
     * Returns a store that wraps any value type with a fixed TTL.
     * All entries expire {@code ttl} after insertion regardless of value type.
     */
    static <K, V> TimedExpiryCache<K, V> timed(int maxSize, Duration ttl) {
        return new TimedExpiryCache<>(maxSize, ttl);
    }
}
