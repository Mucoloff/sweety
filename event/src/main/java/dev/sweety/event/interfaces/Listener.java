package dev.sweety.event.interfaces;

import dev.sweety.event.IEvent;

import java.util.function.Consumer;

@FunctionalInterface
public interface Listener<E extends IEvent> extends Consumer<E> {

    void call(E event);

    @Override
    default void accept(E event){
        call(event);
    }
}