package dev.sweety.config.common.serialization;

import dev.sweety.config.common.ConfigurationSection;

import java.util.Map;
import java.util.TreeMap;

public interface ConfigSerializable {

    @Deprecated
    default Map<String, Object> serialize() {
        Map<String, Object> map = new TreeMap<>();
        serialize(ConfigurationSection.fromMap(map));
        return map;
    }

    default void serialize(ConfigurationSection section) {
        Map<String, Object> map = serialize();
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                section.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Writes this object into the given sink at the current field path.
     */
    default void write(ConfigSink sink) {
        Map<String, Object> map = new TreeMap<>();
        serialize(ConfigurationSection.fromMap(map));
        sink.writeRawMap(map);
    }

}

