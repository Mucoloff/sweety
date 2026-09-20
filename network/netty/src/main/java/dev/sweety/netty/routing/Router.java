package dev.sweety.netty.routing;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Axiomatic network topology routing primitive.
 * Maps an incoming message {@code T} and the live pool of candidate targets {@code C}
 * to a selected target.
 *
 * @param <T> message or envelope type
 * @param <C> target channel, peer, or backend node type
 */
@FunctionalInterface
public interface Router<T, C> {

    /**
     * Selects the target from the candidate list for the given message.
     *
     * @param message incoming message or request context
     * @param targets active, healthy candidate peers (non-empty)
     * @return the chosen target, or {@code null} if no viable target exists
     */
    C route(@NotNull T message, @NotNull List<C> targets);
}
