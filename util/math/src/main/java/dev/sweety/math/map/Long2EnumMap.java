package dev.sweety.math.map;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance primitive-keyed {@code long -> E} enum map.
 * Features a dense fast path for small non-negative keys ({@code 0 <= key < 256})
 * and a sparse {@link Long2ObjectOpenHashMap} fallback for arbitrary long keys.
 */
public final class Long2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface LongEnumConsumer<E> {
        void accept(long key, E value);
    }

    private static final int DENSE_BOUND = 256;

    private final Class<E> valueType;
    @SuppressWarnings("unchecked")
    private final E[] dense = (E[]) new Enum<?>[DENSE_BOUND];
    private final long[] densePresent = new long[DENSE_BOUND >> 6];

    private Long2ObjectOpenHashMap<E> sparse;
    private int size;

    private Long2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Long2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Long2EnumMap<>(valueType);
    }

    public Class<E> valueType() {
        return valueType;
    }

    private boolean isDensePresent(int key) {
        return (densePresent[key >> 6] & (1L << (key & 63))) != 0;
    }

    private void markDensePresent(int key) {
        densePresent[key >> 6] |= (1L << (key & 63));
    }

    private void markDenseAbsent(int key) {
        densePresent[key >> 6] &= ~(1L << (key & 63));
    }

    public @Nullable E put(long key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        if ((key & ~0xFFL) == 0L) {
            int idx = (int) key;
            E prev = dense[idx];
            if (!isDensePresent(idx)) {
                markDensePresent(idx);
                size++;
            }
            dense[idx] = value;
            return prev;
        }
        if (sparse == null) {
            sparse = new Long2ObjectOpenHashMap<>();
        }
        E prev = sparse.put(key, value);
        if (prev == null) {
            size++;
        }
        return prev;
    }

    public @Nullable E get(long key) {
        if ((key & ~0xFFL) == 0L) {
            int idx = (int) key;
            return isDensePresent(idx) ? dense[idx] : null;
        }
        return sparse != null ? sparse.get(key) : null;
    }

    public @Nullable E getOrDefault(long key, @Nullable E defaultValue) {
        if ((key & ~0xFFL) == 0L) {
            int idx = (int) key;
            return isDensePresent(idx) ? dense[idx] : defaultValue;
        }
        if (sparse != null) {
            E val = sparse.get(key);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    public boolean containsKey(long key) {
        if ((key & ~0xFFL) == 0L) {
            return isDensePresent((int) key);
        }
        return sparse != null && sparse.containsKey(key);
    }

    public @Nullable E remove(long key) {
        if ((key & ~0xFFL) == 0L) {
            int idx = (int) key;
            if (!isDensePresent(idx)) return null;
            markDenseAbsent(idx);
            E prev = dense[idx];
            dense[idx] = null;
            size--;
            return prev;
        }
        if (sparse != null) {
            E prev = sparse.remove(key);
            if (prev != null) {
                size--;
            }
            return prev;
        }
        return null;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        if (size == 0) return;
        Arrays.fill(dense, null);
        Arrays.fill(densePresent, 0L);
        if (sparse != null) {
            sparse.clear();
        }
        size = 0;
    }

    public void forEachEntry(@NotNull LongEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (int i = 0; i < DENSE_BOUND; i++) {
            if (isDensePresent(i)) {
                action.accept(i, dense[i]);
            }
        }
        if (sparse != null && !sparse.isEmpty()) {
            for (var entry : sparse.long2ObjectEntrySet()) {
                action.accept(entry.getLongKey(), entry.getValue());
            }
        }
    }
}
