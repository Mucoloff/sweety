package dev.sweety.core.event.interfaces;

import dev.sweety.core.event.IEvent;

import java.util.function.Consumer;

@FunctionalInterface
public interface Listener<E extends IEvent> extends Consumer<E> {

    void call(E event);

    @Override
    default void accept(E event){
        call(event);
    }
}