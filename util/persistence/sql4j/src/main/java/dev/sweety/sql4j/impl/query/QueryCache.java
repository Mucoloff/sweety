package dev.sweety.sql4j.impl.query;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A multi-bucket cache for SQL4J. 
 * Uses separate internal caches to avoid 'Recursive Update' exceptions when a cached object
 * (like a Query) tries to access the cache for its own sub-components (like Metadata).
 */
public final class QueryCache {

    private static final Cache<String, Object> METADATA_CACHE = createCache();
    private static final Cache<String, Object> QUERY_CACHE = createCache();

    private static Cache<String, Object> createCache() {
        return Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();
    }

    /**
     * Cache for query metadata (SQL templates, reflection, etc.)
     */
    @SuppressWarnings("unchecked")
    public static <T> T getMetadata(String key, Function<String, T> loader) {
        return (T) METADATA_CACHE.get(key, loader);
    }

    /**
     * Cache for full Query objects.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getQuery(String key, Function<String, T> loader) {
        return (T) QUERY_CACHE.get(key, loader);
    }

    /**
     * Legacy support for the generic get. Uses the metadata bucket by default.
     */
    public static <T> T get(String key, Function<String, T> loader) {
        return getMetadata(key, loader);
    }

    public static void clear() {
        METADATA_CACHE.invalidateAll();
        QUERY_CACHE.invalidateAll();
    }

    private QueryCache() {}
}