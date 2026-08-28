package dev.sweety.versioning.lifecycle;

/**
 * Standard lifecycle abstraction for modular runtime applications and bootstrap components.
 * Follows the 3-stage contract: {@link #load()}, {@link #start()}, {@link #shutdown()}.
 */
public interface Lifecycle {

    /**
     * Initializes resources, configuration, and dependencies prior to active execution.
     */
    default void load() throws Exception {}

    /**
     * Starts active execution, listeners, or loops.
     */
    default void start() throws Exception {}

    /**
     * Gracefully stops active processes, closes connections, and releases held resources.
     */
    default void shutdown() throws Exception {}
}
