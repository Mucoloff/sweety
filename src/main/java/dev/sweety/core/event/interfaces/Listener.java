package dev.sweety.core.event.interfaces;

@FunctionalInterface
public interface Listener<Event> {

    void call(Event event);
}