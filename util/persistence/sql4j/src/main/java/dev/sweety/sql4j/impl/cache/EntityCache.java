package dev.sweety.sql4j.impl.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCache {

    private final Map<Class<?>, Map<Object, Object>> cache = new ConcurrentHashMap<>();
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Object pk) {
        if (!enabled) return null;
        Map<Object, Object> classCache = cache.get(clazz);
        return classCache != null ? (T) classCache.get(pk) : null;
    }

    public <T> void put(Class<T> clazz, Object pk, T entity) {
        if (!enabled) return;
        cache.computeIfAbsent(clazz, _ -> new ConcurrentHashMap<>()).put(pk, entity);
    }

    public void evict(Class<?> clazz, Object pk) {
        Map<Object, Object> classCache = cache.get(clazz);
        if (classCache != null) {
            classCache.remove(pk);
        }
    }

    public void evictAll(Class<?> clazz) {
        cache.remove(clazz);
    }

    public void clear() {
        cache.clear();
    }
}
