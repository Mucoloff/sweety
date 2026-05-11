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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.sweety.thread.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> priorityFilter =
            Comparator.comparingInt(EventCallback::priority);

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Object> CONTAINER_CACHE = new ConcurrentHashMap<>();
    private final ThreadManager threadManager;

    public EventSystem(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    public EventSystem() {
        this(new ThreadManager("event-dispatcher"));
    }

    @Override
    public <T extends Event<?>> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state) {
        subscribe(eventType, listener, priority, state, !MutableEvent.class.isAssignableFrom(eventType));
    }

    private <T extends Event<?>> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state, boolean readOnly) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        java.util.Objects.requireNonNull(listener, "listener cannot be null");
        java.util.Objects.requireNonNull(state, "state cannot be null");
        final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
        final Object container = CONTAINER_CACHE.computeIfAbsent(eventType, _ -> new Object());
        callSites.add(new EventCallback<>(container, listener, priority, state, readOnly));
        callSites.sort(priorityFilter);
    }

    @Override
    public <T extends Event<?>> SubscriptionBuilder<T> on(@NotNull Class<T> eventType) {
        java.util.Objects.requireNonNull(eventType, "eventType cannot be null");
        return new SubscriptionBuilderImpl<>(this, eventType);
    }

    private static class SubscriptionBuilderImpl<T extends Event<?>> implements SubscriptionBuilder<T> {
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
    public <T extends Event<?>> void unsubscribe(final Class<T> eventType) {
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

            final Listener<? extends Event<?>> listener;
            try {
                listener = (Listener<? extends Event<?>>) LOOKUP.unreflectGetter(field).invokeWithArguments(container);
            } catch (Throwable ignored) {
                continue;
            }

            boolean readOnly = annotation.readOnly();
            if (!readOnly && eventType instanceof Class<?> clazz) {
                readOnly = !MutableEvent.class.isAssignableFrom(clazz);
            }

            final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
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
    public <T extends Event<?>> T dispatch(@NotNull T event) {
        java.util.Objects.requireNonNull(event, "event cannot be null");
        if (event instanceof CancellableEvent<?> me) me.uncancel();

        List<EventCallback<T>> callbacks = findCompatibleCallbacks(event);
        if (callbacks.isEmpty()) return event;

        final int initialHash = event.hashCode();

        int i = 0;
        while (i < callbacks.size()) {
            EventCallback<T> first = callbacks.get(i);

            if (first.readOnly()) {
                List<EventCallback<T>> parallelGroup = new ArrayList<>(callbacks.size());
                int priority = first.priority();
                while (i < callbacks.size() && callbacks.get(i).readOnly() && callbacks.get(i).priority() == priority) {
                    parallelGroup.add(callbacks.get(i));
                    i++;
                }
                executeParallel(parallelGroup, event);
            } else {
                executeSequential(first, event);
                i++;
            }

            if (isCancelled(event)) break;
        }

        if (initialHash != event.hashCode()) {
            if (event instanceof AbstractEvent<?> ae) {
                ae.setChanged(true);
            }
        }

        return event;
    }

    private <T extends Event<?>> List<EventCallback<T>> findCompatibleCallbacks(T event) {
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
            Collections.addAll(toCheck, clazz.getInterfaces());
        }

        result.sort(priorityFilter);
        return result;
    }

    private <T extends Event<?>> void executeSequential(EventCallback<T> cb, T event) {
        if (shouldCall(cb, event)) {
            if (cb.readOnly()) {
                T copy = wrapImmutable(event);
                cb.listener().call(copy);
                if (isCancelled(copy)) cancel(event);
            } else {
                cb.listener().call(event);
            }
        }
    }

    private <T extends Event<?>> void executeParallel(List<EventCallback<T>> group, T event) {
        if (group.isEmpty()) return;

        T immutableEvent = wrapImmutable(event);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (EventCallback<T> cb : group) {
            if (shouldCall(cb, event)) {
                futures.add(threadManager.fireAndForget(ThreadType.CACHED, thread -> {
                    cb.listener().call(immutableEvent);
                    return CompletableFuture.completedFuture(null);
                }));
            }
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            if (isCancelled(immutableEvent)) {
                cancel(event);
            }
        }
    }

    private void cancel(Event<?> event) {
        if (event instanceof CancellableEvent<?> ce) {
            ce.cancel();
        }
    }

    private <T extends Event<?>> T wrapImmutable(T event) {
        if (event instanceof MutableEvent<?> me) {
            //noinspection unchecked
            return (T) me.toImmutable();
        }
        return event;
    }

    private boolean isCancelled(Event<?> event) {
        if (event instanceof CancellableEvent<?> ce) {
            return ce.isCancelled();
        }
        return false;
    }

    private boolean shouldCall(EventCallback<?> cb, Event<?> event) {
        return cb.state() == State.BOTH ||
                (cb.state() == State.PRE && event.isPre()) ||
                (cb.state() == State.POST && event.isPost());
    }


    @Override
    public <T extends MutableEvent<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        final T e = dispatch(event);

        if (isCancelled(e)) return Pair.of(e, null);

        R call = original.call(e.isChanged() ? changedArgsMapper.apply(e) : args);

        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    @Override
    public <T extends Event<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Object... args
    ) {
        final T e = dispatch(event);

        if (isCancelled(e)) return Pair.of(e, null);

        R call = original.call(args);

        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    private record EventCallback<T extends Event<?>>(Object container, Listener<T> listener, int priority, State state,
                                                      boolean readOnly) {
    }
}