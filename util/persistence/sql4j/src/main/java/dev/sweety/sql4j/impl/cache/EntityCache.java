package dev.sweety.sql4j.impl.cache;

import dev.sweety.sql4j.api.annotation.Cacheable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityCache {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private final Map<Class<?>, Map<Object, Object>> caches = new ConcurrentHashMap<>();
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Object pk) {
        if (!enabled || pk == null) return null;
        Map<Object, Object> classCache = caches.get(clazz);
        return classCache != null ? (T) classCache.get(pk) : null;
    }

    public <T> void put(Class<T> clazz, Object pk, T entity) {
        if (!enabled || pk == null || entity == null) return;
        
        Cacheable anno = clazz.getAnnotation(Cacheable.class);
        if (anno == null) return; // Only cache if @Cacheable is present

        int maxSize = anno.maxSize() > 0 ? anno.maxSize() : DEFAULT_MAX_SIZE;

        caches.computeIfAbsent(clazz, _ -> Collections.synchronizedMap(new LinkedHashMap<Object, Object>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                return size() > maxSize;
            }
        })).put(pk, entity);
    }

    public void evict(Class<?> clazz, Object pk) {
        if (pk == null) return;
        Map<Object, Object> classCache = caches.get(clazz);
        if (classCache != null) {
            classCache.remove(pk);
        }
    }

    public void evictAll(Class<?> clazz) {
        caches.remove(clazz);
    }

    public void clear() {
        caches.clear();
    }
}
