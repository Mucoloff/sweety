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

    public <T> void put(Class<T> clazz, Object pk, T entity) {
        if (entity == null || pk == null) return;
        getCache(clazz).put(pk, entity);
    }

    public <T> T get(Class<T> clazz, Object pk) {
        Cache<Object, Object> cache = caches.get(clazz);
        if (cache == null) return null;
        //noinspection unchecked
        return (T) cache.getIfPresent(pk);
    }

    public void evict(Class<?> clazz, Object pk) {
        Cache<Object, Object> cache = caches.get(clazz);
        if (cache != null) {
            cache.invalidate(pk);
        }
    }

    public void evictAll(Class<?> clazz) {
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
            int maxSize = DEFAULT_MAX_SIZE;
            Cacheable ann = k.getAnnotation(Cacheable.class);
            if (ann != null) {
                maxSize = ann.maxSize();
            }
            
            return Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .build();
        });
    }

    public boolean isEnabled() {
        return true;
    }
}
