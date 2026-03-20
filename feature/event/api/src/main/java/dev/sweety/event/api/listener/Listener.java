package dev.sweety.event.api.listener;

import dev.sweety.event.api.IEvent;

import java.util.function.Consumer;

@FunctionalInterface
public interface Listener<E extends IEvent> extends Consumer<E> {

    void call(E event);

    @Override
    default void accept(E event) {
        call(event);
    }
}

