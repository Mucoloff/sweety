package dev.sweety.service;

@FunctionalInterface
public interface HealthCheck {
    HealthStatus checkHealth();
}
