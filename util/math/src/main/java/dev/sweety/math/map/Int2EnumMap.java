package dev.sweety.math.map;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance primitive-keyed {@code int -> E} enum map.
 * Optimized with a hybrid layout:
 * <ul>
 *   <li>Dense array for bounded non-negative keys ({@code 0 <= key < 256}), offering $O(1)$ zero-hashing lookups (perfect for JVM opcodes, packet IDs, and byte indices).</li>
 *   <li>Sparse {@link Int2ObjectOpenHashMap} fallback for negative or large keys.</li>
 * </ul>
 */
public final class Int2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface IntEnumConsumer<E> {
        void accept(int key, E value);
    }

    private static final int DENSE_BOUND = 256;

    private final Class<E> valueType;
    @SuppressWarnings("unchecked")
    private final E[] dense = (E[]) new Enum<?>[DENSE_BOUND];
    private final long[] densePresent = new long[DENSE_BOUND >> 6]; // 4 longs = 256 bits

    private Int2ObjectOpenHashMap<E> sparse;
    private int size;

    private Int2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Int2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Int2EnumMap<>(valueType);
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

    public @Nullable E put(int key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        if ((key & ~0xFF) == 0) { // 0 <= key < 256
            E prev = dense[key];
            if (!isDensePresent(key)) {
                markDensePresent(key);
                size++;
            }
            dense[key] = value;
            return prev;
        }
        if (sparse == null) {
            sparse = new Int2ObjectOpenHashMap<>();
        }
        E prev = sparse.put(key, value);
        if (prev == null) {
            size++;
        }
        return prev;
    }

    public @Nullable E get(int key) {
        if ((key & ~0xFF) == 0) {
            return isDensePresent(key) ? dense[key] : null;
        }
        return sparse != null ? sparse.get(key) : null;
    }

    public @Nullable E getOrDefault(int key, @Nullable E defaultValue) {
        if ((key & ~0xFF) == 0) {
            return isDensePresent(key) ? dense[key] : defaultValue;
        }
        if (sparse != null) {
            E val = sparse.get(key);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    public boolean containsKey(int key) {
        if ((key & ~0xFF) == 0) {
            return isDensePresent(key);
        }
        return sparse != null && sparse.containsKey(key);
    }

    public @Nullable E remove(int key) {
        if ((key & ~0xFF) == 0) {
            if (!isDensePresent(key)) return null;
            markDenseAbsent(key);
            E prev = dense[key];
            dense[key] = null;
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

    public void forEachEntry(@NotNull IntEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (int i = 0; i < DENSE_BOUND; i++) {
            if (isDensePresent(i)) {
                action.accept(i, dense[i]);
            }
        }
        if (sparse != null && !sparse.isEmpty()) {
            for (var entry : sparse.int2ObjectEntrySet()) {
                action.accept(entry.getIntKey(), entry.getValue());
            }
        }
    }
}
