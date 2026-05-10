package dev.sweety.feature.service.api;

/**
 * Represents a service with a lifecycle.
 */
public interface Service {

    /**
     * Called when the service is enabled.
     */
    default void onEnable() {}

    /**
     * Called when the service is disabled.
     */
    default void onDisable() {}

}
