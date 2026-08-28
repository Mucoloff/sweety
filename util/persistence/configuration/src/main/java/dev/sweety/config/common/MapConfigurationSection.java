package dev.sweety.config.common;

import dev.sweety.config.common.serialization.ConfigSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A lightweight implementation of {@link ConfigurationSection} that reads and writes
 * directly to a {@link Map}. It handles dot-separated path resolution without
 * building or maintaining a node tree cache.
 */
public class MapConfigurationSection implements ConfigurationSection {

    private final Map<String, Object> map;
    private final String basePath;

    public MapConfigurationSection(Map<String, Object> map) {
        this(map, "");
    }

    public MapConfigurationSection(Map<String, Object> map, String basePath) {
        this.map = map;
        this.basePath = basePath;
    }

    @Override
    public String path() {
        return basePath;
    }

    private Map<String, Object> getMapForPath(String path, boolean create) {
        if (path == null || path.isEmpty()) return map;
        String[] parts = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map<?, ?>) {
                //noinspection unchecked
                current = (Map<String, Object>) next;
            } else if (create) {
                Map<String, Object> newMap = new TreeMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            } else {
                return null;
            }
        }
        return current;
    }

    private String getLastPart(String path) {
        if (path == null || path.isEmpty()) return "";
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    @Override
    public Object get(String path) {
        if (path == null || path.isEmpty()) return map;
        Map<String, Object> nodeMap = getMapForPath(path, false);
        if (nodeMap != null) {
            return nodeMap.get(getLastPart(path));
        }
        return null;
    }

    @Override
    public boolean contains(String path) {
        return get(path) != null;
    }

    @Override
    public void set(String path, Object value) {
        if (path == null || path.isEmpty()) return;
        Map<String, Object> nodeMap = getMapForPath(path, true);
        if (nodeMap == null) return;

        String key = getLastPart(path);
        if (value == null) {
            nodeMap.remove(key);
        } else {
            nodeMap.put(key, serializeValue(value));
        }
    }

    /** Mirrors {@code Configuration.serializeValue} so nested {@link ConfigSerializable}s unroll here too. */
    private static Object serializeValue(Object value) {
        return switch (value) {
            case ConfigSerializable s -> {
                Map<String, Object> nested = new TreeMap<>();
                s.serialize(new MapConfigurationSection(nested));
                yield nested;
            }
            case List<?> l -> l.stream().map(MapConfigurationSection::serializeValue).toList();
            case Map<?, ?> m -> {
                Map<String, Object> nested = new TreeMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) nested.put(String.valueOf(e.getKey()), serializeValue(e.getValue()));
                yield nested;
            }
            case null, default -> value;
        };
    }

    @Override
    public @Nullable Map<String, Object> getMap(String path) {
        Object val = get(path);
        if (val instanceof Map<?, ?>) {
            //noinspection unchecked
            return (Map<String, Object>) val;
        }
        return null;
    }

    @Override
    public @Nullable ConfigurationSection getSection(String path) {
        Map<String, Object> sectionMap = getMap(path);
        return sectionMap != null ? new MapConfigurationSection(sectionMap, path(path)) : null;
    }
}
