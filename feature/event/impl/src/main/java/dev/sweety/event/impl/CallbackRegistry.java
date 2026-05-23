package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

class CallbackRegistry {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static final Comparator<EventCallback<?>> CALLBACK_ORDER =
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

    final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    /** Per-event-type token for listener-only subscriptions (not tied to a user container). */
    private final Map<Type, Object> syntheticContainerByEventType = new ConcurrentHashMap<>();
    private final AtomicLong registrationGeneration = new AtomicLong(0);
    private final AtomicLong subscribeOrderSeq = new AtomicLong(0);

    <T extends Event<?>> void subscribe(@NotNull Class<T> eventType, @NotNull Listener<T> listener,
                                        int priority, @NotNull State state, boolean readOnly) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        List<EventCallback<?>> callSites = callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
        Object container = syntheticContainerByEventType.computeIfAbsent(eventType, _ -> new Object());
        insertCallback(callSites, new EventCallback<>(container, listener, priority, state, readOnly, subscribeOrderSeq.incrementAndGet()));
        notifyCallSiteMutation(eventType);
    }

    void subscribe(@NotNull Object container) {
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

            List<EventCallback<?>> callSites = callSiteMap.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>());
            insertCallback(callSites, new EventCallback<>(container, listener, spec.priority, spec.state, readOnly, subscribeOrderSeq.incrementAndGet()));
            mutated = true;
            lastMutatedType = eventType;
        }
        if (mutated) {
            notifyCallSiteMutation(lastMutatedType);
        }
    }

    <T extends Event<?>> void unsubscribe(Class<T> eventType) {
        List<EventCallback<?>> callSites = callSiteMap.get(eventType);
        if (callSites != null) {
            callSites.clear();
            notifyCallSiteMutation(eventType);
        }
    }

    void unsubscribe(Object container) {
        for (Map.Entry<Type, List<EventCallback<?>>> entry : callSiteMap.entrySet()) {
            List<EventCallback<?>> callSites = entry.getValue();
            if (callSites.removeIf(cb -> cb.container() == container)) {
                notifyCallSiteMutation(entry.getKey());
            }
        }
    }

    void insertCallback(List<EventCallback<?>> list, EventCallback<?> callback) {
        int i = Collections.binarySearch(list, callback, CALLBACK_ORDER);
        if (i < 0) {
            i = -i - 1;
        }
        list.add(i, callback);
    }

    void notifyCallSiteMutation(Type registrationType) {
        registrationGeneration.incrementAndGet();
    }

    List<EventCallback<?>> callSites(Type eventType) {
        return callSiteMap.get(eventType);
    }

    long generation() {
        return registrationGeneration.get();
    }
}
