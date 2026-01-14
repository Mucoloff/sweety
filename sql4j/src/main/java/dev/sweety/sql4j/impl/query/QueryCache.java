package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.query.Query;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class QueryCache {

    private static final ConcurrentMap<String, Query<?>> CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> Query<T> get(
            String key,
            Supplier<Query<T>> supplier
    ) {
        return (Query<T>) CACHE.computeIfAbsent(key, k -> supplier.get());
    }

    public static void clear() {
        CACHE.clear();
    }
}
