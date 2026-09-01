package dev.sweety.service;

/**
 * Base interface for all Sweety managed services.
 */
public interface Service {
    default String name() {
        return getClass().getSimpleName();
    }
}
