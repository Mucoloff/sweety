package dev.sweety.time.store;

import dev.sweety.math.pool.ObjectPool;
import dev.sweety.time.Expirable;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * {@link ExpiryStore} that wraps any value type with a fixed TTL by associating a
 * monotonic deadline ({@code System.currentTimeMillis() + ttlMillis}) with each entry.
 *
 * <p>Backed by {@link ExpiryCache}; the wrapping is transparent to the caller.
 */
public class TimedExpiryCache<K, V> implements ExpiryStore<K, V> {

    private final ExpiryCache<K, Entry<V>> internal;

    private final ObjectPool<Entry<V>> entryPool = new ObjectPool.Builder<Entry<V>>(Entry::new, ObjectPool.Strategy.SHARED)
            .reset(e -> e.value = null)
            .build();

    private final long ttlNanos;

    protected TimedExpiryCache(int maxSize, Duration ttl) {
        this.ttlNanos = ttl.toNanos();
        this.internal = new ExpiryCache<>(maxSize, entryPool::release);
    }

    @Override
    public void add(K key, V value) {
        Entry<V> entry = entryPool.acquire();
        entry.value = value;
        entry.expireAt = System.nanoTime() + ttlNanos;
        internal.add(key, entry);
    }

    @Override
    public @Nullable V get(K key) {
        Entry<V> e = internal.get(key);
        return e == null ? null : e.value();
    }

    @Override
    public @Nullable V consume(K key) {
        Entry<V> e = internal.consume(key);
        if (e == null) return null;
        V value = e.value();
        entryPool.release(e);
        return value;
    }

    @Override
    public void remove(K key) {
        internal.remove(key);
    }

    @Override
    public void cleanUp() {
        internal.cleanUp();
    }

    private static final class Entry<V> implements Expirable {
        private V value;
        private long expireAt;

        private Entry() {
            // for ObjectPool
        }

        public V value() {
            return value;
        }

        @Override
        public long expireAt() {
            return expireAt;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry<?> entry)) return false;
            return expireAt == entry.expireAt && Objects.equals(value, entry.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, expireAt);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Entry.class.getSimpleName() + "[", "]")
                    .add("value=" + value)
                    .add("expireAt=" + expireAt)
                    .toString();
        }
    }
}
