package dev.sweety.event.api;

import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.api.function.Operation;
import it.unimi.dsi.fastutil.Pair;

import java.util.function.Function;

public interface EventSystem {
    <T extends Event<?>> void subscribe(Class<T> eventType, Listener<T> listener, int priority, State state);

    <T extends Event<?>> SubscriptionBuilder<T> on(Class<T> eventType);

    <T extends Event<?>> void unsubscribe(final Class<T> eventType);

    void subscribe(Object container);

    void unsubscribe(Object container);

    <T extends Event<?>> T dispatch(T event);

    <T extends MutableEvent<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    );

    <T extends Event<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Object... args
    );

}
