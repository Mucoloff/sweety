package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class ExecutionPlanner {

    sealed interface ExecutionStep<T extends Event<?>> {
        void run(T event, EventDispatcher dispatcher);
    }

    record ParallelStep<T extends Event<?>>(List<EventCallback<T>> group) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventDispatcher dispatcher) {
            dispatcher.executeParallel(group, event);
        }
    }

    record SequentialStep<T extends Event<?>>(EventCallback<T> cb) implements ExecutionStep<T> {
        @Override
        public void run(T event, EventDispatcher dispatcher) {
            dispatcher.executeSequential(cb, event);
        }
    }

    private record CachedPlan(long generation, List<ExecutionStep<?>> steps) {
    }

    private final Map<Class<?>, CachedPlan> executionPlanCache = new ConcurrentHashMap<>();

    void invalidate() {
        executionPlanCache.clear();
    }

    @SuppressWarnings("unchecked")
    <T extends Event<?>> List<ExecutionStep<T>> planFor(Class<?> eventClass, CallbackRegistry registry) {
        long currentGen;
        List<ExecutionStep<?>> built;
        do {
            currentGen = registry.generation();
            CachedPlan cached = executionPlanCache.get(eventClass);
            if (cached != null && cached.generation == currentGen) {
                return (List<ExecutionStep<T>>) (Object) cached.steps;
            }
            built = buildExecutionPlan(eventClass, registry);
        } while (registry.generation() != currentGen);

        executionPlanCache.put(eventClass, new CachedPlan(currentGen, built));
        return (List<ExecutionStep<T>>) (Object) built;
    }

    @SuppressWarnings("unchecked")
    List<ExecutionStep<?>> buildExecutionPlan(Class<?> eventClass, CallbackRegistry registry) {
        List<EventCallback<Event<?>>> callbacks = findCompatibleCallbacks(eventClass, registry.callSiteMap);
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
    <T extends Event<?>> List<EventCallback<T>> findCompatibleCallbacks(
            Class<?> eventClass,
            java.util.Map<Type, List<EventCallback<?>>> callSiteMap) {

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

        result.sort(CallbackRegistry.CALLBACK_ORDER);
        return result;
    }
}
