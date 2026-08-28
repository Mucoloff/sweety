package dev.sweety.time.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalListener;
import dev.sweety.time.Expirable;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@link ExpiryStore} backed by a Caffeine cache for {@link Expirable} values.
 *
 * <p>Caffeine handles expiry transparently via per-entry TTL derived from
 * {@link Expirable#expiryTime()} (nanoseconds remaining). {@link #get} uses
 * {@code getIfPresent} so Caffeine's expiry filter already applies — no secondary
 * {@code expired()} check needed. {@link #consume} uses the raw backing map to
 * atomically remove; it re-checks {@code expired()} because raw map access bypasses
 * Caffeine's filter.
 */
public class ExpiryCache<K, V extends Expirable> implements ExpiryStore<K, V> {

    private final Cache<K, V> cache;
    private final @Nullable Consumer<V> evictionListener;

    protected ExpiryCache(int maxSize) {
        this(maxSize, null);
    }

    protected ExpiryCache(int maxSize, @Nullable Consumer<V> evictionListener) {
        this.evictionListener = evictionListener;
        var builder = Caffeine.newBuilder()
                .maximumSize(maxSize);
        if (evictionListener != null) {
            builder.removalListener((RemovalListener<K, V>) (key, value, cause) -> {
                if (value != null) evictionListener.accept(value);
            });
        }
        this.cache = builder
                .expireAfter(new Expiry<K, V>() {
                    @Override
                    public long expireAfterCreate(K key, V value, long currentTime) {
                        if (!value.hasExpiry()) return Long.MAX_VALUE;
                        // expiryTime() returns remaining nanos (nanoTime-based) — no conversion needed
                        return Math.max(0L, value.expiryTime());
                    }

                    @Override
                    public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }


    @Override
    public void add(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public @Nullable V get(K key) {
        // getIfPresent respects Caffeine's expiry policy — no secondary expired() check needed
        return cache.getIfPresent(key);
    }

    @Override
    public @Nullable V consume(K key) {
        // asMap().remove bypasses Caffeine's filter and removalListener → re-check expiry manually
        V value = cache.asMap().remove(key);
        if (value == null) return null;
        if (value.expired()) {
            if (evictionListener != null) evictionListener.accept(value);
            return null;
        }
        return value;
    }

    @Override
    public void remove(K key) {
        cache.invalidate(key);
    }

    @Override
    public void cleanUp() {
        cache.cleanUp();
    }
}
