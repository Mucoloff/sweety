package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.LongPredicate;

/**
 * Thread-safe primitive-keyed {@code long -> V} map with lock striping (à la {@link java.util.concurrent.ConcurrentHashMap}),
 * without boxing the key the way a {@code Map<Long,V>} would. The key space is split into N independent
 * {@link Long2ObjectOpenHashMap} segments, each guarded by its own monitor.
 * <p>
 * Single-key ops ({@link #get}/{@link #put}/{@link #remove(long)}/{@link #remove(long, Object)}/{@link #containsKey})
 * are atomic on their segment. Bulk ops ({@link #size}/{@link #forEachEntry}/{@link #long2ObjectEntrySet}) are
 * <b>weakly consistent</b>: they operate on a per-segment snapshot and never throw
 * {@link java.util.ConcurrentModificationException}. Absent keys return {@link #defaultReturnValue()} (null unless set).
 */
public final class Long2ObjectConcurrentOpenHashMap<V> extends AbstractLong2ObjectMap<V> {

    /** Callback for {@link #forEachEntry} that receives the key without boxing. */
    @FunctionalInterface
    public interface LongObjConsumer<V> {
        void accept(long key, V value);
    }

    private static final int DEFAULT_SEGMENTS = 16;

    private final int mask;
    private final Long2ObjectOpenHashMap<V>[] seg;

    @SuppressWarnings("unchecked")
    private Long2ObjectConcurrentOpenHashMap(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Long2ObjectOpenHashMap[s];
        for (int i = 0; i < s; i++) seg[i] = new Long2ObjectOpenHashMap<>();
    }

    /** New map with the default segment count (16). */
    public static <V> Long2ObjectConcurrentOpenHashMap<V> create() {
        return new Long2ObjectConcurrentOpenHashMap<>(DEFAULT_SEGMENTS);
    }

    /** New map with a chosen concurrency level (rounded up to a power of two). */
    public static <V> Long2ObjectConcurrentOpenHashMap<V> withSegments(int segments) {
        return new Long2ObjectConcurrentOpenHashMap<>(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private Long2ObjectOpenHashMap<V> seg(long k) {
        return seg[(int) (HashCommon.mix(k) & mask)];
    }

    @Override
    public V get(long k) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.get(k); }
    }

    @Override
    public V put(long k, V v) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.put(k, v); }
    }

    @Override
    public V remove(long k) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.remove(k); }
    }

    /** Atomically insert {@code v} only if {@code k} is absent; returns the existing value if present (else null). */
    public V putIfAbsent(long k, V v) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) {
            V existing = s.get(k);
            if (existing != null || s.containsKey(k)) return existing;
            s.put(k, v);
            return null;
        }
    }

    /** Atomically remove {@code k} only if it is currently mapped to {@code value}. */
    public boolean remove(long k, Object value) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) {
            if (s.containsKey(k) && Objects.equals(s.get(k), value)) {
                s.remove(k);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean containsKey(long k) {
        Long2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.containsKey(k); }
    }

    @Override
    public int size() {
        long n = 0;
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) { s.clear(); }
    }

    /** Remove every key matching {@code keyPredicate}. Returns true if anything was removed. */
    public boolean removeIfKey(LongPredicate keyPredicate) {
        boolean changed = false;
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            changed |= s.keySet().removeIf(keyPredicate);
        }
        return changed;
    }

    /**
     * Weakly-consistent iteration without key boxing. Snapshots each segment under its lock, then invokes
     * {@code action} OUTSIDE the locks — so {@code action} may safely re-enter this map (e.g. remove the
     * just-visited key) without risking a deadlock.
     */
    public void forEachEntry(LongObjConsumer<? super V> action) {
        java.util.ArrayList<long[]> kChunks = new java.util.ArrayList<>();
        java.util.ArrayList<Object[]> vChunks = new java.util.ArrayList<>();
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            int sz = s.size();
            if (sz == 0) continue;
            long[] ks = new long[sz];
            Object[] vs = new Object[sz];
            int i = 0;
            for (Long2ObjectMap.Entry<V> e : s.long2ObjectEntrySet()) {
                ks[i] = e.getLongKey();
                vs[i] = e.getValue();
                i++;
            }
            kChunks.add(ks);
            vChunks.add(vs);
        }
        for (int c = 0; c < kChunks.size(); c++) {
            long[] keys = kChunks.get(c);
            Object[] vals = vChunks.get(c);
            for (int i = 0; i < keys.length; i++) {
                @SuppressWarnings("unchecked")
                V v = (V) vals[i];
                action.accept(keys[i], v);
            }
        }
    }

    /** Weakly-consistent snapshot entry set (satisfies the {@link Long2ObjectMap} contract). */
    @Override
    public @NotNull ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        ObjectSet<Long2ObjectMap.Entry<V>> out = new ObjectOpenHashSet<>();
        for (Long2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            for (Long2ObjectMap.Entry<V> e : s.long2ObjectEntrySet()) {
                out.add(new BasicEntry<>(e.getLongKey(), e.getValue()));
            }
        }
        return out;
    }
}
