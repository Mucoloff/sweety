package dev.sweety.time.store;

import dev.sweety.time.Expirable;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * {@link ExpiryStore} that wraps any value type with a fixed TTL by associating a
 * monotonic deadline ({@code System.currentTimeMillis() + ttlMillis}) with each entry.
 *
 * <p>Backed by {@link ExpiryCache}; the wrapping is transparent to the caller.
 */
public class TimedExpiryCache<K, V> implements ExpiryStore<K, V> {

    private final ExpiryCache<K, Entry<V>> internal;
    private final long ttlNanos;

    public TimedExpiryCache(int maxSize, Duration ttl) {
        this.internal = new ExpiryCache<>(maxSize);
        this.ttlNanos = ttl.toNanos();
    }

    @Override
    public void add(K key, V value) {
        internal.add(key, new Entry<>(value, System.nanoTime() + ttlNanos));
    }

    @Override
    public @Nullable V get(K key) {
        Entry<V> e = internal.get(key);
        return e == null ? null : e.value();
    }

    @Override
    public @Nullable V consume(K key) {
        Entry<V> e = internal.consume(key);
        return e == null ? null : e.value();
    }

    @Override
    public void remove(K key) {
        internal.remove(key);
    }

    @Override
    public void cleanUp() {
        internal.cleanUp();
    }

    private record Entry<V>(V value, long expireAt) implements Expirable {}
}
