package dev.sweety.event.processor;

import dev.sweety.core.math.Operation;
import dev.sweety.event.Event;
import dev.sweety.event.EventSystem;
import it.unimi.dsi.fastutil.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EventMapping {

    private final Map<Class<?>, Function<Object, ? extends Event>> events = new ConcurrentHashMap<>();

    private final EventSystem eventSystem;

    public EventMapping(EventSystem eventSystem) {
        this.eventSystem = eventSystem;
    }

    public <T extends Event> T dispatch(Object obj) {
        //noinspection unchecked
        final Function<Object, T> function = (Function<Object, T>) events.get(obj.getClass());
        if (function == null) return null;
        final T event = function.apply(obj);
        return eventSystem.dispatch(event);
    }

    public <T extends Event, R> Pair<T, R> dispatchWrapped(
            Object obj,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        //noinspection unchecked
        final Function<Object, T> function = (Function<Object, T>) events.get(obj.getClass());
        if (function == null) return null;
        final T event = function.apply(obj);
        return eventSystem.dispatchWrapped(event, original, changedArgsMapper, args);
    }

    public void registerEventMapping(Class<? extends Event> eventClass, Class<?> clazz) {
        Function<Object, ? extends Event> construct = p -> {
            try {
                return eventClass.getConstructor(clazz).newInstance(p);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        };

        events.put(clazz, construct);
    }
}
