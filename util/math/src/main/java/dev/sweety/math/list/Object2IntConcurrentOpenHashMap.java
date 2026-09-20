package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Thread-safe {@code K -> int} map with lock striping (à la {@link java.util.concurrent.ConcurrentHashMap}),
 * without boxing the value the way a {@code Map<K, Integer>} would.
 * Lock striping over N independent {@link Object2IntOpenHashMap} segments.
 */
public final class Object2IntConcurrentOpenHashMap<K> extends AbstractObject2IntMap<K> {

    private static final int DEFAULT_SEGMENTS = 16;
    private static final int ABSENT = -1;

    private final int mask;
    private final Object2IntOpenHashMap<K>[] seg;

    @SuppressWarnings("unchecked")
    private Object2IntConcurrentOpenHashMap(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Object2IntOpenHashMap[s];
        for (int i = 0; i < s; i++) {
            Object2IntOpenHashMap<K> m = new Object2IntOpenHashMap<>();
            m.defaultReturnValue(ABSENT);
            seg[i] = m;
        }
        this.defaultReturnValue(ABSENT);
    }

    /** New map with default segment count (16). */
    public static <K> Object2IntConcurrentOpenHashMap<K> create() {
        return new Object2IntConcurrentOpenHashMap<>(DEFAULT_SEGMENTS);
    }

    /** New map with a chosen concurrency level (rounded up to a power of two). */
    public static <K> Object2IntConcurrentOpenHashMap<K> withSegments(int segments) {
        return new Object2IntConcurrentOpenHashMap<>(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private Object2IntOpenHashMap<K> seg(Object k) {
        return seg[HashCommon.mix(Objects.hashCode(k)) & mask];
    }

    @Override
    public int getInt(Object k) {
        Object2IntOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.getInt(k); }
    }

    @Override
    public int put(K k, int v) {
        Object2IntOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.put(k, v); }
    }

    @Override
    public int removeInt(Object k) {
        Object2IntOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.removeInt(k); }
    }

    @Override
    public boolean containsKey(Object k) {
        Object2IntOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.containsKey(k); }
    }

    @Override
    public int size() {
        long n = 0;
        for (Object2IntOpenHashMap<K> s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Object2IntOpenHashMap<K> s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (Object2IntOpenHashMap<K> s : seg) synchronized (s) { s.clear(); }
    }

    /** Remove every key matching {@code keyPredicate}. Returns true if anything was removed. */
    public boolean removeIfKey(Predicate<? super K> keyPredicate) {
        boolean changed = false;
        for (Object2IntOpenHashMap<K> s : seg) synchronized (s) {
            changed |= s.keySet().removeIf(keyPredicate);
        }
        return changed;
    }

    /** Weakly-consistent snapshot entry set (satisfies the {@link Object2IntMap} contract). */
    @Override
    public @NotNull ObjectSet<Object2IntMap.Entry<K>> object2IntEntrySet() {
        ObjectSet<Object2IntMap.Entry<K>> out = new ObjectOpenHashSet<>();
        for (Object2IntOpenHashMap<K> s : seg) synchronized (s) {
            for (Object2IntMap.Entry<K> e : s.object2IntEntrySet()) {
                out.add(new AbstractObject2IntMap.BasicEntry<>(e.getKey(), e.getIntValue()));
            }
        }
        return out;
    }
}
