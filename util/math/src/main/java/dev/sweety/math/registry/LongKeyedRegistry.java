package dev.sweety.math.registry;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;

/**
 * Thread-safe primitive-long-keyed registry — the shared shape for anything that today keys a live set
 * (connections, sessions, ...) on a String/boxed-Long identity computed per lookup (e.g.
 * {@code Channel.id().asLongText()}) instead of a cheap {@code long} assigned once. Backed by fastutil's
 * {@link Long2ObjectOpenHashMap} (no key boxing) wrapped in {@link Long2ObjectMaps#synchronize} — puts/
 * removes only happen on connect/disconnect, so a coarse lock costs nothing where it matters (the
 * per-packet {@link #get}).
 */
public final class LongKeyedRegistry<T> {

    private final Long2ObjectMap<T> map = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    public void put(long id, T value) {
        map.put(id, value);
    }

    public T remove(long id) {
        return map.remove(id);
    }

    public T get(long id) {
        return map.get(id);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    /** Live, unmodifiable view — safe to iterate under the registry's lock via external synchronization if needed. */
    public Collection<T> values() {
        return java.util.Collections.unmodifiableCollection(map.values());
    }
}
