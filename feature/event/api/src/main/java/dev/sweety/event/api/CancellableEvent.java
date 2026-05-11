package dev.sweety.event.api;

/**
 * Interface for events that can be cancelled.
 * @param <E> The type of the event itself.
 */
public interface CancellableEvent<E extends CancellableEvent<E>> extends Event<E> {

    void cancel();

    void uncancel();

    boolean isCancelled();
}
