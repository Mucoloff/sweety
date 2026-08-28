package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

class CallbackRegistry {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static final Comparator<EventCallback<?>> CALLBACK_ORDER =
            Comparator.<EventCallback<?>>comparingInt(EventCallback::priority)
                    .thenComparingLong(EventCallback::subscribeOrder);

    private record LinkFieldSpec(Field field, Type eventType, int priority, State state, boolean annotationReadOnly,
                                 boolean parallel) {
    }

    private static final ClassValue<List<LinkFieldSpec>> LINK_FIELDS = new ClassValue<>() {
        @Override
        protected List<LinkFieldSpec> computeValue(@NotNull Class<?> type) {
            List<LinkFieldSpec> out = new ArrayList<>();
            // Walk the whole hierarchy: @LinkEvent listener fields declared on an abstract base
            // (e.g. EntityEspBase) must register for concrete subclasses too.
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields()) {
                    LinkEvent ann = f.getAnnotation(LinkEvent.class);
                    if (ann == null) continue;
                    Type eventType;
                    try {
                        eventType = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                    } catch (Throwable ignore) {
                        continue;
                    }
                    int p = ann.priority() == -1 ? ann.level().getValue() : ann.priority();
                    out.add(new LinkFieldSpec(f, eventType, p, ann.state(), ann.readOnly(), ann.parallel()));
                }
            return List.copyOf(out);
        }
    };

    final Map<Type, List<EventCallback<?>>> callSiteMap = new ConcurrentHashMap<>();
    /**
     * Per-event-type token for listener-only subscriptions (not tied to a user container).
     */
    private final Map<Type, Object> syntheticContainerByEventType = new ConcurrentHashMap<>();
    private final AtomicLong registrationGeneration = new AtomicLong(0);
    private final AtomicLong subscribeOrderSeq = new AtomicLong(0);

    <T extends Event<?>> void subscribe(@NotNull Class<T> eventType, @NotNull Listener<T> listener,
                                        int priority, @NotNull State state, boolean readOnly) {
        subscribe(eventType, listener, priority, state, readOnly, true);
    }

    <T extends Event<?>> void subscribe(@NotNull Class<T> eventType, @NotNull Listener<T> listener,
                                        int priority, @NotNull State state, boolean readOnly, boolean parallel) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        List<EventCallback<?>> callSites = callSiteMap.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        Object container = syntheticContainerByEventType.computeIfAbsent(eventType, ignored -> new Object());
        insertCallback(callSites, new EventCallback<>(container, listener, priority, state, readOnly, parallel, subscribeOrderSeq.incrementAndGet()));
        notifyCallSiteMutation(eventType);
    }

    void subscribe(@NotNull Object container) {
        Objects.requireNonNull(container, "container cannot be null");
        boolean mutated = false;
        Type lastMutatedType = null;
        for (LinkFieldSpec spec : LINK_FIELDS.get(container.getClass())) {
            Field field = spec.field;
            Type eventType = spec.eventType;
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            // Force-open the field and read it with plain reflection. A MethodHandle unreflectGetter
            // uses THIS module's lookup, which can't access a field in another module even when public
            // (JPMS) — fails on NeoForge's module layers. setAccessible + Field.get works for open
            // (automatic) modules and unnamed modules alike.
            try {
                field.setAccessible(true);
            } catch (Throwable ignore) {
                // not openable — skip
            }

            Listener<? extends Event<?>> listener;
            try {
                listener = (Listener<? extends Event<?>>) field.get(isStatic ? null : container);
            } catch (Throwable ex) {
                continue;
            }
            if (listener == null) continue;

            boolean readOnly = spec.annotationReadOnly;
            // Non-mutable events can safely run listeners in parallel (read-only view).
            // Listeners that need to mutate MC state must use mc.execute() themselves.
            if (!readOnly && eventType instanceof Class<?> clazz) {
                readOnly = !MutableEvent.class.isAssignableFrom(clazz);
            }

            List<EventCallback<?>> callSites = callSiteMap.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
            insertCallback(callSites, new EventCallback<>(container, listener, spec.priority, spec.state, readOnly, spec.parallel, subscribeOrderSeq.incrementAndGet()));
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
