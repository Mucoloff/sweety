package dev.sweety.sql4j.impl.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sweety.sql4j.api.annotation.Cacheable;

import dev.sweety.math.list.ConcurrentHashSet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCache {

    private static final int DEFAULT_MAX_SIZE = 1000;

    // Cache map: EntityClass -> (PK -> EntityInstance)
    private final Map<Class<?>, Cache<Object, Object>> caches = new ConcurrentHashMap<>();
    private final Set<Class<?>> cacheableClasses = ConcurrentHashSet.create();
    private final Set<Class<?>> nonCacheableClasses = ConcurrentHashSet.create();

    private volatile boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCacheable(Class<?> clazz) {
        if (!enabled) return false;
        if (cacheableClasses.contains(clazz)) return true;
        if (nonCacheableClasses.contains(clazz)) return false;
        // Check-then-add across two sets isn't atomic like the old computeIfAbsent was; a race just
        // means two threads redundantly call getAnnotation() for the same class and agree on the
        // (deterministic) result, never a wrong or torn value.
        boolean cacheable = clazz.getAnnotation(Cacheable.class) != null;
        (cacheable ? cacheableClasses : nonCacheableClasses).add(clazz);
        return cacheable;
    }

    public <T> void put(Class<T> clazz, Object pk, T entity) {
        if (entity == null || pk == null || !isCacheable(clazz)) return;
        getCache(clazz).put(pk, entity);
    }

    public <T> T get(Class<T> clazz, Object pk) {
        if (!isCacheable(clazz)) return null;
        Cache<Object, Object> cache = caches.get(clazz);
        if (cache == null) return null;
        //noinspection unchecked
        return (T) cache.getIfPresent(pk);
    }

    public void evict(Class<?> clazz, Object pk) {
        if (!isCacheable(clazz)) return;
        Cache<Object, Object> cache = caches.get(clazz);
        if (cache != null) {
            cache.invalidate(pk);
        }
    }

    public void evictAll(Class<?> clazz) {
        if (!isCacheable(clazz)) return;
        Cache<Object, Object> cache = caches.get(clazz);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    public void clear() {
        for (Cache<Object, Object> cache : caches.values()) {
            cache.invalidateAll();
        }
    }

    private Cache<Object, Object> getCache(Class<?> clazz) {
        return caches.computeIfAbsent(clazz, k -> {
            Cacheable ann = k.getAnnotation(Cacheable.class);
            // @Cacheable with no explicit maxSize() (or maxSize()<=0) used to build an unbounded
            // Caffeine cache — DEFAULT_MAX_SIZE was declared but never wired in. Bound it now so
            // a hot entity class can't grow the L2 cache without limit.
            int maxSize = (ann != null && ann.maxSize() > 0) ? ann.maxSize() : DEFAULT_MAX_SIZE;

            Caffeine<Object, Object> builder = Caffeine.newBuilder();
            builder.maximumSize(maxSize);

            if (ann != null && ann.ttlSeconds() > 0) {
                builder.expireAfterWrite(ann.ttlSeconds(), ann.ttlUnit());
            }

            return builder.build();
        });
    }

}
