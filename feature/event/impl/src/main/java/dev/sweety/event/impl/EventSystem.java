package dev.sweety.event.impl;

import dev.sweety.event.api.*;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.api.function.Operation;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventSystem implements IEventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback<?>> CALLBACK_ORDER =
            Comparator.<EventCallback<?>>comparingInt(EventCallback::priority)
                    .thenComparingLong(EventCallback::subscribeOrder);

    private record LinkFieldSpec(Field field, Type eventType, int priority, State state, boolean annotationReadOnly) {
    }

    private static final ClassValue<List<LinkFieldSpec>> LINK_FIELDS = new ClassValue<>() {
        @Override
        protected List<LinkFieldSpec> computeValue(Class<?> type) {
            List<LinkFieldSpec> out = new ArrayList<>();
            for (Field f : type.getDeclaredFields()) {
                LinkEvent ann = f.getAnnotation(LinkEvent.class);
                if (ann == null) continue;
                Type eventType;
                try {
                    eventType = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                } catch (Throwable ignore) {
                    continue;
                }
                int p = ann.priority() == -1 ? ann.level().getValue() : ann.priority();
                out.add(new LinkFieldSpec(f, eventType, p, ann.state(), ann.readOnly()));
            }
            return List.copyOf(out);
        }
    };

    private record CachedPlan(long generation, List<ExecutionStep<?>> steps) {
    }

    private final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    /** Per-event-type token for listener-only subscriptions (not tied to a user container). */
    private final Map<Type, Object> syntheticContainerByEventType = new ConcurrentHashMap<>();
    private final ThreadManager threadManager;
    private final Executor asyncExecutor;
    private final AtomicLong registrationGeneration = new AtomicLong(0);
    private final AtomicLong subscribeOrderSeq = new AtomicLong(0);
    private final Map<Class<?>, CachedPlan> executionPlanCache = new ConcurrentHashMap<>();

    private sealed interface ExecutionStep<T extends Event<?>> {
        void run(T event, EventSystem system);
    }

    private record ParallelStep<T extends Event<?>>(List<EventCallback<T>> group) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventSystem system) {
            system.executeParallel(group, event);
        }
    }

    private record SequentialStep<T extends Event<?>>(EventCallback<T> cb) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventSystem system) {
            system.executeSequential(cb, event);
        }
    }

    public EventSystem(ThreadManager threadManager, Executor asyncExecutor) {
        this.threadManager = threadManager;
        this.asyncExecutor = asyncExecutor;
    }

    public EventSystem(ThreadManager threadManager) {
        this(threadManager, null);
    }

    public EventSystem() {
        this(new ThreadManager("event-dispatcher"));
    }

    private void insertCallback(List<EventCallback<?>> list, EventCallback<?> callback) {
        int i = Collections.binarySearch(list, callback, CALLBACK_ORDER);
        if (i < 0) {
            i = -i - 1;
        }
        list.add(i, callback);
    }

    private void notifyCallSiteMutation(Type registrationType) {
        registrationGeneration.incrementAndGet();
        if (!(registrationType instanceof Class<?>)) {
            executionPlanCache.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Event<?>> List<ExecutionStep<T>> executionPlanFor(Class<?> eventClass) {
        long currentGen;
        List<ExecutionStep<?>> built;
        do {
            currentGen = registrationGeneration.get();
            CachedPlan cached = executionPlanCache.get(eventClass);
            if (cached != null && cached.generation == currentGen) {
                return (List<ExecutionStep<T>>) (Object) cached.steps;
            }
            built = buildExecutionPlan(eventClass);
        } while (registrationGeneration.get() != currentGen);

        executionPlanCache.put(eventClass, new CachedPlan(currentGen, built));
        return (List<ExecutionStep<T>>) (Object) built;
    }

    @Override
    public <T extends Event<?>> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state) {
        subscribe(eventType, listener, priority, state, !MutableEvent.class.isAssignableFrom(eventType));
    }

    private <T extends Event<?>> void subscribe(@NotNull final Class<T> eventType, @NotNull final Listener<T> listener, int priority, @NotNull State state, boolean readOnly) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
        Object container = syntheticContainerByEventType.computeIfAbsent(eventType, _ -> new Object());
        insertCallback(callSites, new EventCallback<>(container, listener, priority, state, readOnly, subscribeOrderSeq.incrementAndGet()));
        notifyCallSiteMutation(eventType);
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
        List<EventCallback<?>> callSites = this.callSiteMap.get(eventType);
        if (callSites != null) {
            callSites.clear();
            notifyCallSiteMutation(eventType);
        }
    }

    @Override
    public void subscribe(@NotNull Object container) {
        Objects.requireNonNull(container, "container cannot be null");
        boolean mutated = false;
        Type lastMutatedType = null;
        for (LinkFieldSpec spec : LINK_FIELDS.get(container.getClass())) {
            Field field = spec.field;
            Type eventType = spec.eventType;
            if (!field.canAccess(container)) field.setAccessible(true);

            Listener<? extends Event<?>> listener;
            try {
                //noinspection unchecked
                listener = (Listener<? extends Event<?>>) LOOKUP.unreflectGetter(field).invokeWithArguments(container);
            } catch (Throwable ignored) {
                continue;
            }

            boolean readOnly = spec.annotationReadOnly;
            if (!readOnly && eventType instanceof Class<?> clazz) {
                readOnly = !MutableEvent.class.isAssignableFrom(clazz);
            }

            List<EventCallback<?>> callSites = this.callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
            insertCallback(callSites, new EventCallback<>(container, listener, spec.priority, spec.state, readOnly, subscribeOrderSeq.incrementAndGet()));
            mutated = true;
            lastMutatedType = eventType;
        }
        if (mutated) {
            notifyCallSiteMutation(lastMutatedType);
        }
    }

    @Override
    public void unsubscribe(final Object container) {
        for (Map.Entry<Type, List<EventCallback<?>>> entry : callSiteMap.entrySet()) {
            List<EventCallback<?>> callSites = entry.getValue();
            if (callSites.removeIf(cb -> cb.container() == container)) {
                notifyCallSiteMutation(entry.getKey());
            }
        }
    }

    @Override
    public <T extends Event<?>> T dispatch(@NotNull T event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (event instanceof CancellableEvent<?> me) me.uncancel();

        List<ExecutionStep<T>> plan = executionPlanFor(event.getClass());

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
                        plan.add(new ParallelStep<>(currentBatch));
                        currentBatch = new ArrayList<>();
                    }
                    plan.add(new SequentialStep<>(cb));
                }
            }
            if (!currentBatch.isEmpty()) {
                plan.add(new ParallelStep<>(currentBatch));
            }
        }
        return plan;
    }

    @SuppressWarnings("unchecked")
    private <T extends Event<?>> List<EventCallback<T>> findCompatibleCallbacks(Class<?> eventClass) {
        List<EventCallback<T>> result = new ArrayList<>();
        Set<Type> seenTypes = new HashSet<>();

        Deque<Class<?>> toCheck = new ArrayDeque<>();
        toCheck.add(eventClass);

        while (!toCheck.isEmpty()) {
            Class<?> clazz = toCheck.remove();
            if (!seenTypes.add(clazz)) continue;

            List<EventCallback<?>> list = callSiteMap.get(clazz);
            if (list != null) {
                for (EventCallback<?> cb : list) {
                    result.add((EventCallback<T>) cb);
                }
            }

            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) toCheck.add(superclass);
            Collections.addAll(toCheck, clazz.getInterfaces());
        }

        result.sort(CALLBACK_ORDER);
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

        if (group.size() == 1) {
            executeSequential(group.getFirst(), event);
            return;
        }

        T immutableEvent = wrapImmutable(event);

        CompletableFuture<?>[] futures = new CompletableFuture[group.size()];
        int idx = 0;
        Executor executor = asyncExecutor;
        
        if (executor != null) {
            for (EventCallback<T> cb : group) {
                if (shouldCall(cb, event)) {
                    futures[idx++] = CompletableFuture.runAsync(() -> cb.listener().call(immutableEvent), executor);
                }
            }
        } else {
            for (EventCallback<T> cb : group) {
                if (shouldCall(cb, event)) {
                    futures[idx++] = threadManager.fireAndForget(ThreadType.CACHED, t ->
                            t.execute(() -> cb.listener().call(immutableEvent))
                    );
                }
            }
        }

        if (idx > 0) {
            CompletableFuture.allOf(idx == futures.length ? futures : java.util.Arrays.copyOf(futures, idx)).join();
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
        return switch (cb.state()) {
            case BOTH -> true;
            case PRE -> event.isPre();
            case POST -> event.isPost();
        };
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

    private record EventCallback<T extends Event<?>>(
            Object container, Listener<T> listener, int priority, State state,
            boolean readOnly, long subscribeOrder) {
    }
}
