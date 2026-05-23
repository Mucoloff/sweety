package dev.sweety.event.processor;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.EventSystemPort;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.function.Operation;
import it.unimi.dsi.fastutil.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EventMapping {

    private final Map<Class<?>, Function<Object, ? extends Event<?>>> mappings = new ConcurrentHashMap<>();

    private final EventSystemPort eventSystem;

    public EventMapping(EventSystemPort eventSystem) {
        this.eventSystem = eventSystem;
    }

    public <T extends Event<T>> T dispatch(Object obj) {
        //noinspection unchecked
        final Function<Object, T> function = (Function<Object, T>) mappings.get(obj.getClass());
        if (function == null) return null;
        final T event = function.apply(obj);
        return eventSystem.dispatch(event);
    }

    public <T extends Event<T>, R> Pair<T, R> dispatchWrapped(
            Object obj,
            Operation<R> original,
            Object... args
    ) {
        //noinspection unchecked
        final Function<Object, T> function = (Function<Object, T>) mappings.get(obj.getClass());
        if (function == null) return null;
        final T event = function.apply(obj);
        return eventSystem.dispatchWrapped(event, original, args);
    }

    public <T extends MutableEvent<T>, R> Pair<T, R> dispatchWrapped(
            Object obj,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        //noinspection unchecked
        final Function<Object, T> function = (Function<Object, T>) mappings.get(obj.getClass());
        if (function == null) return null;
        final T event = function.apply(obj);
        return eventSystem.dispatchWrapped(event, original, changedArgsMapper, args);
    }

    public <E extends Event<?>, T> void registerEventMapping(Class<E> eventClass, Class<T> clazz) {
        Function<Object, E> construct = p -> {
            try {
                return eventClass.getConstructor(clazz).newInstance(p);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        };

        registerEventMapping(clazz, construct);
    }

    public <E extends Event<?>, T> void registerEventMapping(Class<T> clazz, Function<Object, E> constructor) {
        mappings.put(clazz, constructor);
    }

}
