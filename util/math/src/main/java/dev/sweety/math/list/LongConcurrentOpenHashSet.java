package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.AbstractLongSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe primitive {@code long} set with lock striping (à la {@link java.util.concurrent.ConcurrentHashMap}).
 * The key space is partitioned into N independent {@link LongOpenHashSet} segments, each guarded by its own
 * monitor; distinct-segment operations proceed without contention, and no element is ever boxed the way a
 * {@code Set<Long>} would box it.
 * <p>
 * Compared to a {@code ConcurrentHashMap.newKeySet()} of {@code Long}: same concurrency shape (per-segment
 * locking instead of per-bin CAS), zero boxing. Single-element ops ({@link #add}/{@link #remove}/{@link #contains})
 * are atomic. Aggregate ops ({@link #size}/{@link #iterator}/{@link #toLongArray}) are <b>weakly consistent</b>:
 * they take a per-segment snapshot and never throw {@link java.util.ConcurrentModificationException}.
 * Does not permit growth beyond {@link Integer#MAX_VALUE} distinct elements (as any Java set).
 */
public final class LongConcurrentOpenHashSet extends AbstractLongSet {

    private static final int DEFAULT_SEGMENTS = 16;

    private final int mask;
    private final LongOpenHashSet[] seg;

    private LongConcurrentOpenHashSet(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new LongOpenHashSet[s];
        for (int i = 0; i < s; i++) seg[i] = new LongOpenHashSet();
    }

    /** New set with the default segment count (16). */
    public static LongConcurrentOpenHashSet create() {
        return new LongConcurrentOpenHashSet(DEFAULT_SEGMENTS);
    }

    /** New set with a chosen concurrency level (rounded up to a power of two). */
    public static LongConcurrentOpenHashSet withSegments(int segments) {
        return new LongConcurrentOpenHashSet(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private int idx(long v) {
        return (int) (HashCommon.mix(v) & mask);
    }

    @Override
    public boolean add(long v) {
        LongOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.add(v); }
    }

    @Override
    public boolean remove(long v) {
        LongOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.remove(v); }
    }

    @Override
    public boolean contains(long v) {
        LongOpenHashSet s = seg[idx(v)];
        synchronized (s) { return s.contains(v); }
    }

    @Override
    public int size() {
        long n = 0;
        for (LongOpenHashSet s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (LongOpenHashSet s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (LongOpenHashSet s : seg) synchronized (s) { s.clear(); }
    }

    /** Weakly-consistent snapshot iterator — safe to iterate while other threads mutate. */
    @Override
    public @NotNull LongIterator iterator() {
        return snapshot().iterator();
    }

    @Override
    public long[] toLongArray() {
        return snapshot().toLongArray();
    }

    private LongArrayList snapshot() {
        LongArrayList out = new LongArrayList();
        for (LongOpenHashSet s : seg) synchronized (s) { out.addAll(s); }
        return out;
    }
}
