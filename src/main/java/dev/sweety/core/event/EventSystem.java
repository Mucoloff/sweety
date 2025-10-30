package dev.sweety.core.event;




import dev.sweety.core.event.interfaces.LinkEvent;
import dev.sweety.core.event.interfaces.Listener;
import dev.sweety.core.event.interfaces.Operation;

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
import java.util.function.Function;

public class EventSystem {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Comparator<EventCallback> priorityFilter =
            Comparator.comparingInt(EventCallback::priority);

    private final Map<Type, List<EventCallback>> callSiteMap = new ConcurrentHashMap<>();

    public void subscribe(Object object) {
        for (final Field field : object.getClass().getDeclaredFields()) {
            final LinkEvent annotation = field.getAnnotation(LinkEvent.class);
            if (annotation == null) continue;

            Type eventType;
            try {
                eventType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            } catch (Throwable ignore) {
                continue;
            }

            if (!field.canAccess(object)) field.setAccessible(true);

            final Listener<Event> listener;
            try {
                //noinspection unchecked
                listener = (Listener<Event>) LOOKUP.unreflectGetter(field).invokeWithArguments(object);
            } catch (Throwable ignored) {
                continue;
            }

            // Ottimizza: usa CopyOnWriteArrayList (thread-safe e altamente performante per pochi scrittori e molti lettori)
            final List<EventCallback> callSites = this.callSiteMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            callSites.add(new EventCallback(object, listener, annotation.priority() == -1 ? annotation.level().getValue() : annotation.priority(), annotation.state()));
            callSites.sort(priorityFilter);
        }
    }


    public void unsubscribe(final Object object) {
        for (Map.Entry<Type, List<EventCallback>> entry : callSiteMap.entrySet()) {
            final List<EventCallback> callSites = entry.getValue();
            callSites.removeIf(cb -> cb.event() == object);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public <T extends Event> T dispatch(T event) {
        event.setCancelled(false);
        event.setChanged(false);
        final int hash = event.hashCode();
        final List<EventCallback> callbacks = this.callSiteMap.get(event.getClass());
        if (callbacks == null || callbacks.isEmpty()) return event;

        for (Iterator<EventCallback> iterator = callbacks.iterator(); iterator.hasNext(); ) {
            EventCallback cb = iterator.next();

            if (cb.state() == State.BOTH ||
                    (cb.state() == State.PRE && !event.isPost()) ||
                    (cb.state() == State.POST && event.isPost())) {
                cb.listener().call(event);
            }

            if (hash != event.hashCode()) event.setChanged(true);
            if (event.isCancelled()) break;
        }
        return event;
    }

    /**
     * Dispatcha un evento generico con gestione cancel/change e post-event.
     *
     * @param event             evento iniziale
     * @param original          operazione originale da chiamare
     * @param changedArgsMapper funzione che calcola i nuovi argomenti se l'evento è cambiato
     * @param args              argomenti originali da passare all'original.call
     * @param <T>               tipo dell'evento
     */
    public <T extends Event> void dispatchWrapped(
            T event,
            Operation<Void> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args
    ) {
        final T e = dispatch(event);

        if (e.isCancelled()) return;

        original.call(e.isChanged() ? changedArgsMapper.apply(e) : args);

        dispatch(e.post());
    }


    record EventCallback(Object event, Listener<Event> listener, int priority, State state) {
    }
}