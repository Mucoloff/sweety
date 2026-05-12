package dev.sweety.sql4j.impl.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.sweety.sql4j.api.annotation.Cacheable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCache {

    private static final int DEFAULT_MAX_SIZE = 1000;
    
    // Cache map: EntityClass -> (PK -> EntityInstance)
    private final Map<Class<?>, Cache<Object, Object>> caches = new ConcurrentHashMap<>();
    private final Map<Class<?>, Boolean> cacheableStatus = new ConcurrentHashMap<>();

    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCacheable(Class<?> clazz) {
        if (!enabled) return false;
        return cacheableStatus.computeIfAbsent(clazz, k -> {
            Cacheable ann = k.getAnnotation(Cacheable.class);
            return ann != null && ann.maxSize() > 0;
        });
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
            int maxSize = (ann != null && ann.maxSize() > 0) ? ann.maxSize() : DEFAULT_MAX_SIZE;

            Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maxSize);

            if (ann != null && ann.ttlSeconds() > 0) {
                builder.expireAfterWrite(ann.ttlSeconds(), ann.ttlUnit());
            }

            return builder.build();
        });
    }

}
