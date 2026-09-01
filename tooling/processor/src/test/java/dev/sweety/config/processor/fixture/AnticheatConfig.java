package dev.sweety.config.processor.fixture;

import dev.sweety.config.annotation.ConfigKey;
import dev.sweety.config.annotation.GenerateConfig;

@GenerateConfig
public interface AnticheatConfig {
    double maxSpeed();
    int alertThreshold();
    String prefix();

    default boolean enableAlerts() {
        return true;
    }
}
