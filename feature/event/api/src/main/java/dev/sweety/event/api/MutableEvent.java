package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

/**
 * Base interface for all Mutable event views.
 *
 * @param <E> The type of the read-only event view.
 */
public interface MutableEvent<E extends Event<E>> extends Event<E> {

    @NotNull
    E toImmutable();
}
