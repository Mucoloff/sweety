package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe primitive {@code int} set with lock striping. Int counterpart of
 * {@link LongConcurrentOpenHashSet}: the key space is split into N {@link IntOpenHashSet} segments, each
 * under its own monitor, so distinct-segment ops don't contend and no element is boxed the way a
 * {@code Set<Integer>} would. Single-element ops are atomic; aggregate ops are weakly-consistent snapshots.
 */
public final class IntConcurrentOpenHashSet extends AbstractIntSet {

    private static final int DEFAULT_SEGMENTS = 16;

    private final int mask;
    private final IntOpenHashSet[] seg;

    private IntConcurrentOpenHashSet(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new IntOpenHashSet[s];
        for (int i = 0; i < s; i++) seg[i] = new IntOpenHashSet();
    }

    /** New set with the default segment count (16). */
    public static IntConcurrentOpenHashSet create() {
        return new IntConcurrentOpenHashSet(DEFAULT_SEGMENTS);
    }

    /** New set with a chosen concurrency level (rounded up to a power of two). */
    public static IntConcurrentOpenHashSet withSegments(int segments) {
        return new IntConcurrentOpenHashSet(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private int idx(int v) {
        return HashCommon.mix(v) & mask;
    }

    @Override
    public boolean add(int v) {
        IntOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.add(v); }
    }

    @Override
    public boolean remove(int v) {
        IntOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.remove(v); }
    }

    @Override
    public boolean contains(int v) {
        IntOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.contains(v); }
    }

    @Override
    public int size() {
        long n = 0;
        for (IntOpenHashSet s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (IntOpenHashSet s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (IntOpenHashSet s : seg) synchronized (s) { s.clear(); }
    }

    /** Weakly-consistent snapshot iterator — safe to iterate while other threads mutate. */
    @Override
    public @NotNull IntIterator iterator() {
        return snapshot().iterator();
    }

    @Override
    public int[] toIntArray() {
        return snapshot().toIntArray();
    }

    private IntArrayList snapshot() {
        IntArrayList out = new IntArrayList();
        for (IntOpenHashSet s : seg) synchronized (s) { out.addAll(s); }
        return out;
    }
}
