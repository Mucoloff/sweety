package dev.sweety.sql4j.impl.query;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A multi-bucket, instance-based cache for SQL4J.
 * Each {@link dev.sweety.sql4j.impl.Database} owns its own QueryCache instance,
 * ensuring full isolation between different database contexts (e.g. different table names
 * for the same entity class).
 *
 * <p>Uses separate internal caches to avoid 'Recursive Update' exceptions when a cached object
 * (like a Query prototype) tries to access the cache for its own sub-components (like Metadata).
 */
public final class QueryCache {

    private final Cache<String, Object> metadataCache = createCache();
    private final Cache<String, Object> queryCache = createCache();

    private static Cache<String, Object> createCache() {
        return Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }

    /**
     * Cache for query metadata (SQL templates, reflection handles, etc.)
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Function<String, T> loader) {
        return (T) metadataCache.get(key, loader);
    }

    /**
     * Cache for full Query prototype objects.
     */
    @SuppressWarnings("unchecked")
    public <T> T getQuery(String key, Function<String, T> loader) {
        return (T) queryCache.get(key, loader);
    }

    /**
     * Generic get — uses the metadata bucket by default.
     */
    public <T> T get(String key, Function<String, T> loader) {
        return getMetadata(key, loader);
    }

    public void clear() {
        metadataCache.invalidateAll();
        queryCache.invalidateAll();
    }
}