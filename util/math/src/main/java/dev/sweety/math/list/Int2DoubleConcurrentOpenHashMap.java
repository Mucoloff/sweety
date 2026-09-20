package dev.sweety.math.list;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.AbstractInt2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

/**
 * Thread-safe primitive-keyed {@code int -> double} map with lock striping.
 * Completely eliminates boxing for both int keys and double values.
 */
public final class Int2DoubleConcurrentOpenHashMap extends AbstractInt2DoubleMap {

    @FunctionalInterface
    public interface IntDoubleConsumer {
        void accept(int key, double value);
    }

    private static final int DEFAULT_SEGMENTS = 16;
    private static final double DEFAULT_RETURN = 0.0;

    private final int mask;
    private final Int2DoubleOpenHashMap[] seg;

    private Int2DoubleConcurrentOpenHashMap(int segments) {
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Int2DoubleOpenHashMap[s];
        for (int i = 0; i < s; i++) {
            Int2DoubleOpenHashMap m = new Int2DoubleOpenHashMap();
            m.defaultReturnValue(DEFAULT_RETURN);
            seg[i] = m;
        }
        this.defaultReturnValue(DEFAULT_RETURN);
    }

    public static Int2DoubleConcurrentOpenHashMap create() {
        return new Int2DoubleConcurrentOpenHashMap(DEFAULT_SEGMENTS);
    }

    public static Int2DoubleConcurrentOpenHashMap withSegments(int segments) {
        return new Int2DoubleConcurrentOpenHashMap(segments);
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private Int2DoubleOpenHashMap seg(int k) {
        return seg[HashCommon.mix(k) & mask];
    }

    @Override
    public double get(int k) {
        Int2DoubleOpenHashMap s = seg(k);
        synchronized (s) { return s.get(k); }
    }

    @Override
    public double put(int k, double v) {
        Int2DoubleOpenHashMap s = seg(k);
        synchronized (s) { return s.put(k, v); }
    }

    @Override
    public double remove(int k) {
        Int2DoubleOpenHashMap s = seg(k);
        synchronized (s) { return s.remove(k); }
    }

    @Override
    public boolean containsKey(int k) {
        Int2DoubleOpenHashMap s = seg(k);
        synchronized (s) { return s.containsKey(k); }
    }

    public double getOrDefault(int k, double defaultValue) {
        Int2DoubleOpenHashMap s = seg(k);
        synchronized (s) {
            return s.containsKey(k) ? s.get(k) : defaultValue;
        }
    }

    @Override
    public int size() {
        long n = 0;
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) { n += s.size(); }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) { if (!s.isEmpty()) return false; }
        return true;
    }

    @Override
    public void clear() {
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) { s.clear(); }
    }

    public boolean removeIfKey(IntPredicate keyPredicate) {
        boolean changed = false;
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) {
            changed |= s.keySet().removeIf(keyPredicate);
        }
        return changed;
    }

    public void forEachEntry(IntDoubleConsumer action) {
        java.util.ArrayList<int[]> kChunks = new java.util.ArrayList<>();
        java.util.ArrayList<double[]> vChunks = new java.util.ArrayList<>();
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) {
            int sz = s.size();
            if (sz == 0) continue;
            int[] ks = new int[sz];
            double[] vs = new double[sz];
            int i = 0;
            for (Int2DoubleMap.Entry e : s.int2DoubleEntrySet()) {
                ks[i] = e.getIntKey();
                vs[i] = e.getDoubleValue();
                i++;
            }
            kChunks.add(ks);
            vChunks.add(vs);
        }
        for (int c = 0; c < kChunks.size(); c++) {
            int[] keys = kChunks.get(c);
            double[] vals = vChunks.get(c);
            for (int i = 0; i < keys.length; i++) {
                action.accept(keys[i], vals[i]);
            }
        }
    }

    @Override
    public @NotNull ObjectSet<Int2DoubleMap.Entry> int2DoubleEntrySet() {
        ObjectSet<Int2DoubleMap.Entry> out = new ObjectOpenHashSet<>();
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) {
            for (Int2DoubleMap.Entry e : s.int2DoubleEntrySet()) {
                out.add(new BasicEntry(e.getIntKey(), e.getDoubleValue()));
            }
        }
        return out;
    }

    @Override
    public @NotNull it.unimi.dsi.fastutil.ints.IntSet keySet() {
        it.unimi.dsi.fastutil.ints.IntOpenHashSet out = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        for (Int2DoubleOpenHashMap s : seg) synchronized (s) {
            out.addAll(s.keySet());
        }
        return out;
    }
}
