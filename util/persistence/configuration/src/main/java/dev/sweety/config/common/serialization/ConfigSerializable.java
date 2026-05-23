package dev.sweety.config.common.serialization;

import java.util.Map;
import java.util.TreeMap;

public interface ConfigSerializable {
    Map<String, Object> serialize();

    /** Writes this object into the given sink at the current field path. */
    default void write(ConfigSink sink) {
        sink.writeRawMap(serialize());
    }

    default <T> T getAs(Map<String, Object> me, String key) {
        //noinspection unchecked
        return (T) me.get(key);
    }

    default <T, R extends T> R getAs(Map<String, Object> me, String key, Class<T> clazz) {
        Object value = me.get(key);
        if (value instanceof Number n && Number.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            return (R) coerceNumber(n, clazz);
        }
        if (Character.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            return (R) Character.valueOf(value.toString().charAt(0));
        }
        //noinspection unchecked
        return (R) clazz.cast(value);
    }

    private static Number coerceNumber(Number n, Class<?> clazz) {
        if (clazz == Byte.class)    return n.byteValue();
        if (clazz == Short.class)   return n.shortValue();
        if (clazz == Integer.class) return n.intValue();
        if (clazz == Long.class)    return n.longValue();
        if (clazz == Float.class)   return n.floatValue();
        if (clazz == Double.class)  return n.doubleValue();
        return n;
    }

    default Map<String, Object> tree() {
        return new TreeMap<>();
    }

    default Map<String, Object> tree(Map<String, Object> me) {
        return new TreeMap<>(me);
    }

}

