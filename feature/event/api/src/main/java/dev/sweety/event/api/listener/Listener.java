package dev.sweety.event.api.listener;

import dev.sweety.event.api.Event;

@FunctionalInterface
public interface Listener<E extends Event<?>> {

    void call(E event);
}
