package dev.sweety.event.impl;

import org.jetbrains.annotations.NotNull;

import dev.sweety.event.api.IEvent;
import dev.sweety.event.api.IEventSystem;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.api.info.State;
import it.unimi.dsi.fastutil.Pair;
import dev.sweety.event.util.Operation;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> priorityFilter =
            Comparator.comparingInt(EventCallback::priority);

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> CONTAINER_CACHE = new ConcurrentHashMap<>();

    @Override
    public <T extends IEvent> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        java.util.Objects.requireNonNull(listener, "listener cannot be null");
        java.util.Objects.requireNonNull(state, "state cannot be null");
        final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        final Object container = CONTAINER_CACHE.computeIfAbsent(eventType, k -> new Object());
        callSites.add(new EventCallback<>(container, listener, priority, state));
        callSites.sort(priorityFilter);
    }

    @Override
    public <T extends IEvent> dev.sweety.event.api.SubscriptionBuilder<T> on(@NotNull Class<T> eventType) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        return new SubscriptionBuilderImpl<>(this, eventType);
    }

    private static class SubscriptionBuilderImpl<T extends IEvent> implements dev.sweety.event.api.SubscriptionBuilder<T> {
        private final EventSystem system;
        private final Class<T> eventType;
        private int priority = 0;
        private State state = State.BOTH;

        public SubscriptionBuilderImpl(EventSystem system, Class<T> eventType) {
            this.system = system;
            this.eventType = eventType;
        }

        @Override
        public dev.sweety.event.api.SubscriptionBuilder<T> priority(int priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public dev.sweety.event.api.SubscriptionBuilder<T> state(State state) {
            this.state = state;
            return this;
        }

        @Override
        public void handle(Listener<T> listener) {
            system.subscribe(eventType, listener, priority, state);
        }
    }

    @Override
    public <T extends IEvent> void unsubscribe(final Class<T> eventType) {
        final List<EventCallback<?>> callSites = this.callSiteMap.get(eventType);
        if (callSites != null) callSites.clear();
    }


    @Override
    public void subscribe(@NotNull Object container) {
        java.util.Objects.requireNonNull(container, "container cannot be null");
        for (final Field field : container.getClass().getDeclaredFields()) {
            final LinkEvent annotation = field.getAnnotation(LinkEvent.class);
            if (annotation == null) continue;

            Type eventType;
            try {
                eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            } catch (Throwable ignore) {
                continue;
            }

            if (!field.canAccess(container)) field.setAccessible(true);

            final Listener<IEvent> listener;
            try {
                //noinspection unchecked
                listener = (Listener<IEvent>) LOOKUP.unreflectGetter(field).invokeWithArguments(container);
            } catch (Throwable ignored) {
                continue;
            }

            final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            callSites.add(new EventCallback<>(container, listener, annotation.priority() == -1 ? annotation.level().getValue() : annotation.priority(), annotation.state()));
            callSites.sort(priorityFilter);
        }
    }

    @Override
    public void unsubscribe(final Object container) {
        for (Map.Entry<Type, List<EventCallback<?>>> entry : callSiteMap.entrySet()) {
            final List<EventCallback<?>> callSites = entry.getValue();
            callSites.removeIf(cb -> cb.event() == container);
        }
    }

    @Override
    public <T extends IEvent> T dispatch(@NotNull T event) {
        java.util.Objects.requireNonNull(event, "event cannot be null");
        event.setCancelled(false);
        if (event instanceof dev.sweety.event.api.Event e) {
            e.setChanged(false);
        }

        final int hash = event.hashCode();
        final List<EventCallback<?>> callbacks = this.callSiteMap.get(event.getClass());
        if (callbacks == null || callbacks.isEmpty()) return event;

        for (EventCallback<?> callback : callbacks) {
            //noinspection unchecked
            EventCallback<T> cb = (EventCallback<T>) callback;

            if (cb.state() == State.BOTH ||
                    (cb.state() == State.PRE && event.isPre()) ||
                    (cb.state() == State.POST && event.isPost())) {
                cb.listener().call(event);
            }

            if (hash != event.hashCode()) {
                if (event instanceof dev.sweety.event.api.Event e) {
                    e.setChanged(true);
                }
            }
            if (event.isCancelled()) break;
        }
        return event;
    }

    @Override
    public <T extends IEvent, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        final T e = dispatch(event);

        if (e.isCancelled()) return Pair.of(e, null);

        R call = original.call(e.isChanged() ? changedArgsMapper.apply(e) : args);

        //noinspection unchecked
        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    private record EventCallback<T extends IEvent>(Object event, Listener<T> listener, int priority, State state) {
    }
}