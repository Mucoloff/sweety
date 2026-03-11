package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.IEvent;
import dev.sweety.event.api.IEventSystem;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.core.math.function.Operation;
import dev.sweety.event.api.info.State;
import it.unimi.dsi.fastutil.Pair;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> priorityFilter =
            Comparator.comparingInt(EventCallback::priority);

    private static final BiConsumer<IEvent, Boolean> CHANGED = (event, state) -> {
        try {
            Field changed = Event.class.getDeclaredField("changed");
            if (!changed.canAccess(event)) changed.setAccessible(true);
            changed.setBoolean(event, state);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace(System.err);
        }
    };

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> CONTAINER_CACHE = new ConcurrentHashMap<>();

    @Override
    public <T extends IEvent> void subscribe(final Class<T> eventType, final Listener<T> listener, int priority, State state) {
        final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        final Object container = CONTAINER_CACHE.computeIfAbsent(eventType, k -> new Object());
        callSites.add(new EventCallback<>(container, listener, priority, state));
        callSites.sort(priorityFilter);
    }

    public <T extends IEvent> void unsubscribe(final Class<T> eventType) {
        final List<EventCallback<?>> callSites = this.callSiteMap.get(eventType);
        if (callSites != null) callSites.clear();
    }


    @Override
    public void subscribe(Object container) {
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

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Override
    public <T extends IEvent> T dispatch(T event) {
        event.setCancelled(false);
        CHANGED.accept(event, false);

        final int hash = event.hashCode();
        final List<EventCallback<?>> callbacks = this.callSiteMap.get(event.getClass());
        if (callbacks == null || callbacks.isEmpty()) return event;

        for (Iterator<EventCallback<?>> iterator = callbacks.iterator(); iterator.hasNext(); ) {
            //noinspection unchecked
            EventCallback<T> cb = (EventCallback<T>) iterator.next();

            if (cb.state() == State.BOTH ||
                    (cb.state() == State.PRE && !event.isPost()) ||
                    (cb.state() == State.POST && event.isPost())) {
                cb.listener().call(event);
            }

            if (hash != event.hashCode()) CHANGED.accept(event, true);
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