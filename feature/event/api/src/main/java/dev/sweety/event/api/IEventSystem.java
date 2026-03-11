package dev.sweety.event.api;

import dev.sweety.core.math.function.Operation;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;
import it.unimi.dsi.fastutil.Pair;

import java.util.function.Function;

public interface IEventSystem {
    <T extends IEvent> void subscribe(Class<T> eventType, Listener<T> listener, int priority, State state);

    <T extends IEvent> void unsubscribe(final Class<T> eventType);

    void subscribe(Object container);

    void unsubscribe(Object container);

    <T extends IEvent> T dispatch(T event);

    <T extends IEvent, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    );
}
