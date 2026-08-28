package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Thread-safe {@code K -> long} map with lock striping (à la {@link java.util.concurrent.ConcurrentHashMap}),
 * without boxing the value the way a {@code Map<K, Long>} would — sibling of
 * {@link Long2ObjectConcurrentOpenHashMap} for the opposite (object key / primitive value) direction.
 * The key space is split into N independent {@link Object2LongOpenHashMap} segments, each guarded by
 * its own monitor, routed by {@code HashCommon.mix(key.hashCode()) & mask}.
 * <p>
 * Single-key ops ({@link #getLong}/{@link #put}/{@link #removeLong}/{@link #containsKey}) are atomic on
 * their segment. Bulk ops ({@link #size}/{@link #object2LongEntrySet}) are <b>weakly consistent</b>:
 * they operate on a per-segment snapshot and never throw {@link java.util.ConcurrentModificationException}.
 * Absent keys return {@link #defaultReturnValue()}, set to {@code -1L} here to match
 * {@code EndpointRegistry.connectionIdFor}'s existing "-1 = absent" sentinel — zero behavior change at
 * call sites.
 */
public final class Object2LongConcurrentOpenHashMap<K> extends AbstractObject2LongMap<K> {

    private static final int DEFAULT_SEGMENTS = 16;
    private static final long ABSENT = -1L;

    private final int mask;
    private final Object2LongOpenHashMap<K>[] seg;

    @SuppressWarnings("unchecked")
    private Object2LongConcurrentOpenHashMap(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Object2LongOpenHashMap[s];
        for (int i = 0; i < s; i++) {
            Object2LongOpenHashMap<K> m = new Object2LongOpenHashMap<>();
            m.defaultReturnValue(ABSENT);
            seg[i] = m;
        }
        this.defaultReturnValue(ABSENT);
    }

    /** New map with the default segment count (16). */
    public static <K> Object2LongConcurrentOpenHashMap<K> create() {
        return new Object2LongConcurrentOpenHashMap<>(DEFAULT_SEGMENTS);
    }

    /** New map with a chosen concurrency level (rounded up to a power of two). */
    public static <K> Object2LongConcurrentOpenHashMap<K> withSegments(int segments) {
        return new Object2LongConcurrentOpenHashMap<>(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private Object2LongOpenHashMap<K> seg(Object k) {
        return seg[HashCommon.mix(k.hashCode()) & mask];
    }

    @Override
    public long getLong(Object k) {
        Object2LongOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.getLong(k); }
    }

    @Override
    public long put(K k, long v) {
        Object2LongOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.put(k, v); }
    }

    @Override
    public long removeLong(Object k) {
        Object2LongOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.removeLong(k); }
    }

    /** Atomically remove {@code k} only if it is currently mapped to {@code value}. */
    public boolean remove(Object k, long value) {
        Object2LongOpenHashMap<K> s = seg(k);
        synchronized (s) {
            if (s.containsKey(k) && s.getLong(k) == value) {
                s.removeLong(k);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean containsKey(Object k) {
        Object2LongOpenHashMap<K> s = seg(k);
        synchronized (s) { return s.containsKey(k); }
    }

    @Override
    public int size() {
        long n = 0;
        for (Object2LongOpenHashMap<K> s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Object2LongOpenHashMap<K> s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (Object2LongOpenHashMap<K> s : seg) synchronized (s) { s.clear(); }
    }

    /** Remove every key matching {@code keyPredicate}. Returns true if anything was removed. */
    public boolean removeIfKey(Predicate<? super K> keyPredicate) {
        boolean changed = false;
        for (Object2LongOpenHashMap<K> s : seg) synchronized (s) {
            changed |= s.keySet().removeIf(keyPredicate);
        }
        return changed;
    }

    /** Weakly-consistent snapshot entry set (satisfies the {@link Object2LongMap} contract). */
    @Override
    public @NotNull ObjectSet<Object2LongMap.Entry<K>> object2LongEntrySet() {
        ObjectSet<Object2LongMap.Entry<K>> out = new ObjectOpenHashSet<>();
        for (Object2LongOpenHashMap<K> s : seg) synchronized (s) {
            for (Object2LongMap.Entry<K> e : s.object2LongEntrySet()) {
                out.add(new AbstractObject2LongMap.BasicEntry<>(e.getKey(), e.getLongValue()));
            }
        }
        return out;
    }
}
