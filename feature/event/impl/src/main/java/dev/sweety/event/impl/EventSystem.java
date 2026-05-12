package dev.sweety.event.impl;

import dev.sweety.event.api.*;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.util.Operation;
import dev.sweety.thread.ThreadManager;
import dev.sweety.thread.ThreadType;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> priorityFilter = Comparator.comparingInt(EventCallback::priority);

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    /** Per-event-type token for listener-only subscriptions (not tied to a user container). */
    private final Map<Type, Object> syntheticContainerByEventType = new ConcurrentHashMap<>();
    private final ThreadManager threadManager;
    private final Map<Class<?>, List<ExecutionStep<?>>> executionPlanCache = new ConcurrentHashMap<>();

    private sealed interface ExecutionStep<T extends Event<?>> {
        void run(T event, EventSystem system);
    }

    private record ParallelStep<T extends Event<?>>(List<EventCallback<T>> group) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventSystem system) { system.executeParallel(group, event); }
    }

    private record SequentialStep<T extends Event<?>>(EventCallback<T> cb) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventSystem system) { system.executeSequential(cb, event); }
    }

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
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        final List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
        final Object container = syntheticContainerByEventType.computeIfAbsent(eventType, _ -> new Object());
        callSites.add(new EventCallback<>(container, listener, priority, state, readOnly));
        callSites.sort(priorityFilter);
        invalidateExecutionPlansFor(eventType);
    }

    @Override
    public <T extends Event<?>> SubscriptionBuilder<T> on(@NotNull Class<T> eventType) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
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
        if (callSites != null) {
            callSites.clear();
            invalidateExecutionPlansFor(eventType);
        }
    }

    @Override
    public void subscribe(@NotNull Object container) {
        Objects.requireNonNull(container, "container cannot be null");
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
            invalidateExecutionPlansFor(eventType);
        }
    }

    @Override
    public void unsubscribe(final Object container) {
        for (Map.Entry<Type, List<EventCallback<?>>> entry : callSiteMap.entrySet()) {
            final List<EventCallback<?>> callSites = entry.getValue();
            if (callSites.removeIf(cb -> cb.container() == container)) {
                invalidateExecutionPlansFor(entry.getKey());
            }
        }
    }

    @Override
    public <T extends Event<?>> T dispatch(@NotNull T event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (event instanceof CancellableEvent<?> me) me.uncancel();

        // Get or build the execution plan for this specific event class
        //noinspection unchecked
        List<ExecutionStep<T>> plan = (List<ExecutionStep<T>>) (Object) executionPlanCache.computeIfAbsent(event.getClass(), this::buildExecutionPlan);

        if (plan.isEmpty()) return event;

        final int initialHash = event.hashCode();

        for (ExecutionStep<T> step : plan) {
            step.run(event, this);
            if (isCancelled(event)) break;
        }

        if (initialHash != event.hashCode()) {
            if (event instanceof AbstractEvent<?> ae) {
                ae.setChanged(true);
            }
        }

        return event;
    }

    private List<ExecutionStep<?>> buildExecutionPlan(Class<?> eventClass) {
        List<EventCallback<Event<?>>> callbacks = findCompatibleCallbacks(eventClass);
        if (callbacks.isEmpty()) return Collections.emptyList();

        List<ExecutionStep<?>> plan = new ArrayList<>();
        Map<Integer, List<EventCallback<Event<?>>>> priorityGroups = callbacks.stream()
                .collect(Collectors.groupingBy(EventCallback::priority, TreeMap::new, Collectors.toList()));

        boolean isMutableEvent = MutableEvent.class.isAssignableFrom(eventClass);

        for (List<EventCallback<Event<?>>> group : priorityGroups.values()) {
            if (!isMutableEvent) {
                plan.add(new ParallelStep<>(new ArrayList<>(group)));
                continue;
            }

            List<EventCallback<Event<?>>> currentBatch = new ArrayList<>();
            for (EventCallback<Event<?>> cb : group) {
                if (cb.readOnly()) {
                    currentBatch.add(cb);
                } else {
                    if (!currentBatch.isEmpty()) {
                        plan.add(new ParallelStep<>(new ArrayList<>(currentBatch)));
                        currentBatch.clear();
                    }
                    plan.add(new SequentialStep<>(cb));
                }
            }
            if (!currentBatch.isEmpty()) {
                plan.add(new ParallelStep<>(new ArrayList<>(currentBatch)));
            }
        }
        return plan;
    }

    @SuppressWarnings("unchecked")
    private <T extends Event<?>> List<EventCallback<T>> findCompatibleCallbacks(Class<?> eventClass) {
        List<EventCallback<T>> result = new ArrayList<>();
        Set<Type> seenTypes = new HashSet<>();

        Queue<Class<?>> toCheck = new LinkedList<>();
        toCheck.add(eventClass);

        while (!toCheck.isEmpty()) {
            Class<?> clazz = toCheck.poll();
            if (clazz == null || !seenTypes.add(clazz)) continue;

            List<EventCallback<?>> list = callSiteMap.get(clazz);
            if (list != null) {
                for (EventCallback<?> cb : list) {
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
        
        // Optimization: if there's only one listener, run it synchronously
        if (group.size() == 1) {
            executeSequential(group.getFirst(), event);
            return;
        }

        T immutableEvent = wrapImmutable(event);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (EventCallback<T> cb : group) {
            if (shouldCall(cb, event)) {
                futures.add(threadManager.fireAndForget(ThreadType.CACHED, t ->
                        t.execute(() -> cb.listener().call(immutableEvent))
                ));
            }
        }

        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
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

    /**
     * Drops cached execution plans for concrete event classes that would observe this registration key
     * when resolving listeners (subtype relationship).
     */
    private void invalidateExecutionPlansFor(Type registrationType) {
        if (registrationType instanceof Class<?> rc) {
            executionPlanCache.keySet().removeIf(rc::isAssignableFrom);
        } else {
            executionPlanCache.clear();
        }
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

        //noinspection unchecked
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

        //noinspection unchecked
        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    private record EventCallback<T extends Event<?>>(Object container, Listener<T> listener, int priority, State state,
                                                     boolean readOnly) {
    }
}