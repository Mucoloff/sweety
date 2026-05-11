package dev.sweety.event.impl;

import dev.sweety.event.api.*;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.util.Operation;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Function;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> priorityFilter =
            Comparator.comparingInt(EventCallback::priority);

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> CONTAINER_CACHE = new ConcurrentHashMap<>();

    @Override
    public <T extends Event> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state) {
        subscribe(eventType, listener, priority, state, !MutableEvent.class.isAssignableFrom(eventType));
    }

    private <T extends Event> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state, boolean readOnly) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        java.util.Objects.requireNonNull(listener, "listener cannot be null");
        java.util.Objects.requireNonNull(state, "state cannot be null");
        final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        final Object container = CONTAINER_CACHE.computeIfAbsent(eventType, k -> new Object());
        callSites.add(new EventCallback<>(container, listener, priority, state, readOnly));
        callSites.sort(priorityFilter);
    }

    @Override
    public <T extends Event> SubscriptionBuilder<T> on(@NotNull Class<T> eventType) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        return new SubscriptionBuilderImpl<>(this, eventType);
    }

    private static class SubscriptionBuilderImpl<T extends Event> implements SubscriptionBuilder<T> {
        private final EventSystem system;
        private final Class<T> eventType;
        private int priority = 0;
        private State state = State.BOTH;
        private Boolean readOnlyOverride = null;

        public SubscriptionBuilderImpl(EventSystem system, Class<T> eventType) {
            this.system = system;
            this.eventType = eventType;
        }

        @Override
        public SubscriptionBuilder<T> priority(int priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public SubscriptionBuilder<T> state(State state) {
            this.state = state;
            return this;
        }

        @Override
        public SubscriptionBuilder<T> readOnly(boolean readOnly) {
            this.readOnlyOverride = readOnly;
            return this;
        }

        @Override
        public void handle(Listener<T> listener) {
            boolean readOnly = readOnlyOverride != null ? readOnlyOverride : !MutableEvent.class.isAssignableFrom(eventType);
            system.subscribe(eventType, listener, priority, state, readOnly);
        }
    }

    @Override
    public <T extends Event> void unsubscribe(final Class<T> eventType) {
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

            final Listener<Event> listener;
            try {
                //noinspection unchecked
                listener = (Listener<Event>) LOOKUP.unreflectGetter(field).invokeWithArguments(container);
            } catch (Throwable ignored) {
                continue;
            }

            boolean readOnly = annotation.readOnly();
            if (!readOnly && eventType instanceof Class<?> clazz) {
                readOnly = !MutableEvent.class.isAssignableFrom(clazz);
            }

            final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            callSites.add(new EventCallback<>(container, listener, annotation.priority() == -1 ? annotation.level().getValue() : annotation.priority(), annotation.state(), readOnly));
            callSites.sort(priorityFilter);
        }
    }

    @Override
    public void unsubscribe(final Object container) {
        for (Map.Entry<Type, List<EventCallback<?>>> entry : callSiteMap.entrySet()) {
            final List<EventCallback<?>> callSites = entry.getValue();
            callSites.removeIf(cb -> cb.container() == container);
        }
    }

    @Override
    public <T extends Event> T dispatch(@NotNull T event) {
        java.util.Objects.requireNonNull(event, "event cannot be null");
        if (event instanceof MutableEvent me) {
            me.setCancelled(false);
        }

        List<EventCallback<T>> callbacks = findCompatibleCallbacks(event);
        if (callbacks.isEmpty()) return event;

        final int initialHash = event.hashCode();

        int i = 0;
        while (i < callbacks.size()) {
            EventCallback<T> first = callbacks.get(i);
            
            if (first.readOnly()) {
                List<EventCallback<T>> parallelGroup = new ArrayList<>();
                while (i < callbacks.size() && callbacks.get(i).readOnly()) {
                    parallelGroup.add(callbacks.get(i));
                    i++;
                }
                executeParallel(parallelGroup, event);
            } else {
                executeSequential(first, event);
                i++;
            }
            
            if (event.isCancelled()) break;
        }

        if (initialHash != event.hashCode()) {
            if (event instanceof AbstractEvent ae) {
                ae.setChanged(true);
            }
        }

        return event;
    }

    private <T extends Event> List<EventCallback<T>> findCompatibleCallbacks(T event) {
        List<EventCallback<T>> result = new ArrayList<>();
        Set<Type> seenTypes = new HashSet<>();
        
        Queue<Class<?>> toCheck = new LinkedList<>();
        toCheck.add(event.getClass());
        
        while (!toCheck.isEmpty()) {
            Class<?> clazz = toCheck.poll();
            if (clazz == null || !seenTypes.add(clazz)) continue;
            
            List<EventCallback<?>> list = callSiteMap.get(clazz);
            if (list != null) {
                for (EventCallback<?> cb : list) {
                    //noinspection unchecked
                    result.add((EventCallback<T>) cb);
                }
            }
            
            toCheck.add(clazz.getSuperclass());
            for (Class<?> iface : clazz.getInterfaces()) {
                toCheck.add(iface);
            }
        }
        
        result.sort(priorityFilter);
        return result;
    }

    private <T extends Event> void executeSequential(EventCallback<T> cb, T event) {
        if (shouldCall(cb, event)) {
            cb.listener().call(event);
        }
    }

    private <T extends Event> void executeParallel(List<EventCallback<T>> group, T event) {
        if (group.isEmpty()) return;
        
        Event immutableEvent = wrapImmutable(event);

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (EventCallback<T> cb : group) {
                if (shouldCall(cb, event)) {
                    scope.fork(() -> {
                        //noinspection unchecked
                        ((Listener<Event>) cb.listener()).call(immutableEvent);
                        return null;
                    });
                }
            }
            scope.join();
            scope.throwIfFailed();
        } catch (Exception e) {
            throw new RuntimeException("Error in parallel event dispatch", e);
        }
    }
    
    private Event wrapImmutable(Event event) {
        try {
            Method m = event.getClass().getMethod("toImmutable");
            return (Event) m.invoke(event);
        } catch (Exception e) {
            return event; 
        }
    }

    private boolean shouldCall(EventCallback<?> cb, Event event) {
        return cb.state() == State.BOTH ||
                (cb.state() == State.PRE && event.isPre()) ||
                (cb.state() == State.POST && event.isPost());
    }

    @Override
    public <T extends Event, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        final T e = dispatch(event);

        if (e.isCancelled()) return Pair.of(e, null);

        R call = original.call(e.isChanged() ? changedArgsMapper.apply(e) : args);

        //noinspection unchecked
        final T post = (T) dispatch((T) e.post());

        return Pair.of(post, call);
    }

    private record EventCallback<T extends Event>(Object container, Listener<T> listener, int priority, State state, boolean readOnly) {
    }
}