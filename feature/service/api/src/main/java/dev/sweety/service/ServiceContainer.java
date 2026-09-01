package dev.sweety.service;

import java.util.Map;
import java.util.Optional;

public interface ServiceContainer {
    <T> ServiceContainer register(Class<T> type, T instance);
    <T> Optional<T> get(Class<T> type);
    <T> T require(Class<T> type);
    void startAll();
    void stopAll();
    Map<String, HealthStatus> checkHealth();
}
