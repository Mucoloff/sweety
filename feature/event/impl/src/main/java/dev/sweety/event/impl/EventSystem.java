package dev.sweety.event.impl;

import dev.sweety.event.api.*;
import dev.sweety.event.api.function.Operation;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.thread.ThreadManager;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class EventSystem implements EventSystemPort {

    private final CallbackRegistry registry;
    private final ExecutionPlanner planner;
    private final EventDispatcher dispatcher;

    public EventSystem(ThreadManager threadManager, Executor asyncExecutor) {
        this.registry = new CallbackRegistry();
        this.planner = new ExecutionPlanner();
        this.dispatcher = new EventDispatcher(threadManager, asyncExecutor, registry, planner);
    }

    public EventSystem(ThreadManager threadManager) {
        this(threadManager, null);
    }

    public EventSystem() {
        this(new ThreadManager("event-dispatcher"));
    }

    @Override
    public <T extends Event<?>> void subscribe(@NotNull Class<T> eventType, @NotNull Listener<T> listener,
                                               int priority, @NotNull State state) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        boolean readOnly = !MutableEvent.class.isAssignableFrom(eventType);
        registry.subscribe(eventType, listener, priority, state, readOnly);
    }

    /** Package-private entry point used by {@link SubscriptionBuilderImpl}. */
    <T extends Event<?>> void subscribeInternal(@NotNull Class<T> eventType, @NotNull Listener<T> listener,
                                                int priority, @NotNull State state, boolean readOnly) {
        registry.subscribe(eventType, listener, priority, state, readOnly);
    }

    @Override
    public <T extends Event<?>> SubscriptionBuilder<T> on(@NotNull Class<T> eventType) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        return new DefaultSubscriptionBuilder<>(this, eventType);
    }

    @Override
    public <T extends Event<?>> void unsubscribe(Class<T> eventType) {
        registry.unsubscribe(eventType);
    }

    @Override
    public void subscribe(@NotNull Object container) {
        Objects.requireNonNull(container, "container cannot be null");
        registry.subscribe(container);
    }

    @Override
    public void unsubscribe(Object container) {
        registry.unsubscribe(container);
    }

    @Override
    public <T extends Event<?>> T dispatch(@NotNull T event) {
        Objects.requireNonNull(event, "event cannot be null");
        return dispatcher.dispatch(event);
    }

    @Override
    public <T extends MutableEvent<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args) {
        return dispatcher.dispatchWrapped(event, original, changedArgsMapper, args);
    }

    @Override
    public <T extends Event<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Object... args) {
        return dispatcher.dispatchWrapped(event, original, args);
    }
}
