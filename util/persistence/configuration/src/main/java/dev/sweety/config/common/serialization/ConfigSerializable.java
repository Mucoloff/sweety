package dev.sweety.config.common.serialization;

import java.util.Map;
import java.util.TreeMap;

public interface ConfigSerializable {
    Map<String, Object> serialize();

    default <T> T getAs(Map<String, Object> me, String key) {
        //noinspection unchecked
        return (T) me.get(key);
    }

    default <T, R extends T> R getAs(Map<String, Object> me, String key, Class<T> clazz) {
        //noinspection unchecked
        return (R) clazz.cast(me.get(key));
    }

    default Map<String, Object> tree() {
        return new TreeMap<>();
    }

    default Map<String, Object> tree(Map<String, Object> me) {
        return new TreeMap<>(me);
    }

}

