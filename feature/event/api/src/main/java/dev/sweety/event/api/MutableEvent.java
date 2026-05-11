package dev.sweety.event.api;

/**
 * Base interface for all Mutable event views.
 */
public interface MutableEvent extends Event {
    void setCancelled(boolean cancelled);
}
