package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.AbstractInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Thread-safe primitive-keyed {@code int -> V} map with lock striping. Int counterpart of
 * {@link Long2ObjectConcurrentOpenHashMap}: N independent {@link Int2ObjectOpenHashMap} segments, each under
 * its own monitor, no boxing of the key. Single-key ops are atomic; bulk ops are weakly-consistent snapshots.
 * Absent keys return {@link #defaultReturnValue()} (null unless set).
 */
public final class Int2ObjectConcurrentOpenHashMap<V> extends AbstractInt2ObjectMap<V> {

    /** Callback for {@link #forEachEntry} that receives the key without boxing. */
    @FunctionalInterface
    public interface IntObjConsumer<V> {
        void accept(int key, V value);
    }

    private static final int DEFAULT_SEGMENTS = 16;

    private final int mask;
    private final Int2ObjectOpenHashMap<V>[] seg;

    @SuppressWarnings("unchecked")
    private Int2ObjectConcurrentOpenHashMap(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Int2ObjectOpenHashMap[s];
        for (int i = 0; i < s; i++) seg[i] = new Int2ObjectOpenHashMap<>();
    }

    /** New map with the default segment count (16). */
    public static <V> Int2ObjectConcurrentOpenHashMap<V> create() {
        return new Int2ObjectConcurrentOpenHashMap<>(DEFAULT_SEGMENTS);
    }

    /** New map with a chosen concurrency level (rounded up to a power of two). */
    public static <V> Int2ObjectConcurrentOpenHashMap<V> withSegments(int segments) {
        return new Int2ObjectConcurrentOpenHashMap<>(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private Int2ObjectOpenHashMap<V> seg(int k) {
        return seg[HashCommon.mix(k) & mask];
    }

    @Override
    public V get(int k) {
        Int2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.get(k); }
    }

    @Override
    public V put(int k, V v) {
        Int2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.put(k, v); }
    }

    @Override
    public V remove(int k) {
        Int2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.remove(k); }
    }

    /** Atomically remove {@code k} only if it is currently mapped to {@code value}. */
    public boolean remove(int k, Object value) {
        Int2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) {
            if (s.containsKey(k) && Objects.equals(s.get(k), value)) {
                s.remove(k);
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean containsKey(int k) {
        Int2ObjectOpenHashMap<V> s = seg(k);
        synchronized (s) { return s.containsKey(k); }
    }

    @Override
    public int size() {
        long n = 0;
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) { s.clear(); }
    }

    /** Remove every key matching {@code keyPredicate}. Returns true if anything was removed. */
    public boolean removeIfKey(IntPredicate keyPredicate) {
        boolean changed = false;
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            changed |= s.keySet().removeIf(keyPredicate);
        }
        return changed;
    }

    /**
     * Weakly-consistent iteration without key boxing. Snapshots each segment under its lock, then invokes
     * {@code action} OUTSIDE the locks, so {@code action} may safely re-enter this map.
     */
    public void forEachEntry(IntObjConsumer<? super V> action) {
        java.util.ArrayList<int[]> kChunks = new java.util.ArrayList<>();
        java.util.ArrayList<Object[]> vChunks = new java.util.ArrayList<>();
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            int sz = s.size();
            if (sz == 0) continue;
            int[] ks = new int[sz];
            Object[] vs = new Object[sz];
            int i = 0;
            for (Int2ObjectMap.Entry<V> e : s.int2ObjectEntrySet()) {
                ks[i] = e.getIntKey();
                vs[i] = e.getValue();
                i++;
            }
            kChunks.add(ks);
            vChunks.add(vs);
        }
        for (int c = 0; c < kChunks.size(); c++) {
            int[] keys = kChunks.get(c);
            Object[] vals = vChunks.get(c);
            for (int i = 0; i < keys.length; i++) {
                @SuppressWarnings("unchecked")
                V v = (V) vals[i];
                action.accept(keys[i], v);
            }
        }
    }

    /** Weakly-consistent snapshot entry set (satisfies the {@link Int2ObjectMap} contract). */
    @Override
    public @NotNull ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
        ObjectSet<Int2ObjectMap.Entry<V>> out = new ObjectOpenHashSet<>();
        for (Int2ObjectOpenHashMap<V> s : seg) synchronized (s) {
            for (Int2ObjectMap.Entry<V> e : s.int2ObjectEntrySet()) {
                out.add(new BasicEntry<>(e.getIntKey(), e.getValue()));
            }
        }
        return out;
    }
}
