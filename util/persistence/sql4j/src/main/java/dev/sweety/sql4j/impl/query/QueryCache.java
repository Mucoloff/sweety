package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.query.Query;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class QueryCache {

    private static final Cache<String, Query<?>> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public static <T> Query<T> get(String key, Supplier<Query<T>> supplier) {
        //noinspection unchecked
        return (Query<T>) CACHE.get(key, k -> supplier.get());
    }

    public static void clear() {
        CACHE.invalidateAll();
    }
}