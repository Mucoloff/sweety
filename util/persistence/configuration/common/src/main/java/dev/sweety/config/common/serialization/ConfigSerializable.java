package dev.sweety.config.common.serialization;

import java.util.Map;

public interface ConfigSerializable {
    Map<String, Object> serialize();
}

