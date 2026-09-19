package dev.sweety.math.map;

import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Thread-safe primitive-keyed {@code int -> E} enum map with lock striping.
 * Uses N independent {@link Int2EnumMap} segments, each under its own monitor.
 */
public final class Int2EnumConcurrentMap<E extends Enum<E>> {

    private static final int DEFAULT_SEGMENTS = 16;

    private final Class<E> valueType;
    private final int mask;
    private final Int2EnumMap<E>[] seg;

    @SuppressWarnings("unchecked")
    private Int2EnumConcurrentMap(@NotNull Class<E> valueType, int segments) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
        int s = tableSizePow2(segments);
        this.mask = s - 1;
        this.seg = new Int2EnumMap[s];
        for (int i = 0; i < s; i++) {
            this.seg[i] = Int2EnumMap.of(valueType);
        }
    }

    public static <E extends Enum<E>> Int2EnumConcurrentMap<E> of(@NotNull Class<E> valueType) {
        return new Int2EnumConcurrentMap<>(valueType, DEFAULT_SEGMENTS);
    }

    public static <E extends Enum<E>> Int2EnumConcurrentMap<E> of(@NotNull Class<E> valueType, int segments) {
        return new Int2EnumConcurrentMap<>(valueType, segments);
    }

    public Class<E> valueType() {
        return valueType;
    }

    private static int tableSizePow2(int n) {
        int s = 1;
        while (s < n) s <<= 1;
        return Math.max(1, s);
    }

    private int segmentIndex(int k) {
        return HashCommon.mix(k) & mask;
    }

    public @Nullable E put(int key, @NotNull E value) {
        int idx = segmentIndex(key);
        synchronized (seg[idx]) {
            return seg[idx].put(key, value);
        }
    }

    public @Nullable E get(int key) {
        int idx = segmentIndex(key);
        synchronized (seg[idx]) {
            return seg[idx].get(key);
        }
    }

    public @Nullable E getOrDefault(int key, @Nullable E defaultValue) {
        int idx = segmentIndex(key);
        synchronized (seg[idx]) {
            return seg[idx].getOrDefault(key, defaultValue);
        }
    }

    public boolean containsKey(int key) {
        int idx = segmentIndex(key);
        synchronized (seg[idx]) {
            return seg[idx].containsKey(key);
        }
    }

    public @Nullable E remove(int key) {
        int idx = segmentIndex(key);
        synchronized (seg[idx]) {
            return seg[idx].remove(key);
        }
    }

    public int size() {
        int total = 0;
        for (Int2EnumMap<E> s : seg) {
            synchronized (s) {
                total += s.size();
            }
        }
        return total;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void clear() {
        for (Int2EnumMap<E> s : seg) {
            synchronized (s) {
                s.clear();
            }
        }
    }

    public void forEachEntry(@NotNull Int2EnumMap.IntEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (Int2EnumMap<E> s : seg) {
            synchronized (s) {
                s.forEachEntry(action);
            }
        }
    }
}
