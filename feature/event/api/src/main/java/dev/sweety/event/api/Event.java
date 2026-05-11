package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all events (Read-Only view).
 */
public interface Event {

    default void cancel() {
        if (this instanceof MutableEvent me) {
            me.setCancelled(true);
        } else {
            throw new UnsupportedOperationException("Cannot cancel a read-only event");
        }
    }

    @NotNull
    Event post();

    boolean isPost();

    boolean isPre();

    boolean isCancelled();

    boolean isChanged();
}
