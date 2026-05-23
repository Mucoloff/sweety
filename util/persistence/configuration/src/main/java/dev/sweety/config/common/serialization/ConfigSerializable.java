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
        if (value instanceof Number number && Number.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            return (R) switch (clazz.getSimpleName().toLowerCase()) {
                case "byte" -> number.byteValue();
                case "short" -> number.shortValue();
                case "integer" -> number.intValue();
                case "long" -> number.longValue();
                case "float" -> number.floatValue();
                case "double" -> number.doubleValue();
                default -> clazz.cast(value);
            };
        }

        if (Character.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            return (R) Character.valueOf(value.toString().charAt(0));
        }

        //noinspection unchecked
        return (R) clazz.cast(value);
    }

    default Map<String, Object> tree() {
        return new TreeMap<>();
    }

    default Map<String, Object> tree(Map<String, Object> me) {
        return new TreeMap<>(me);
    }

}

