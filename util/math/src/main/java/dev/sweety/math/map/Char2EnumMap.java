package dev.sweety.math.map;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance primitive-keyed {@code char -> E} enum map.
 */
public final class Char2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface CharEnumConsumer<E> {
        void accept(char key, E value);
    }

    private static final int DENSE_BOUND = 256;

    private final Class<E> valueType;
    @SuppressWarnings("unchecked")
    private final E[] dense = (E[]) new Enum<?>[DENSE_BOUND];
    private final long[] densePresent = new long[DENSE_BOUND >> 6];

    private Char2ObjectOpenHashMap<E> sparse;
    private int size;

    private Char2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Char2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Char2EnumMap<>(valueType);
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

    public @Nullable E put(char key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (key < DENSE_BOUND) {
            int idx = key;
            E prev = dense[idx];
            if (!isDensePresent(idx)) {
                markDensePresent(idx);
                size++;
            }
            dense[idx] = value;
            return prev;
        }
        if (sparse == null) {
            sparse = new Char2ObjectOpenHashMap<>();
        }
        E prev = sparse.put(key, value);
        if (prev == null) {
            size++;
        }
        return prev;
    }

    public @Nullable E get(char key) {
        if (key < DENSE_BOUND) {
            return isDensePresent(key) ? dense[key] : null;
        }
        return sparse != null ? sparse.get(key) : null;
    }

    public @Nullable E getOrDefault(char key, @Nullable E defaultValue) {
        if (key < DENSE_BOUND) {
            return isDensePresent(key) ? dense[key] : defaultValue;
        }
        if (sparse != null) {
            E val = sparse.get(key);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    public boolean containsKey(char key) {
        if (key < DENSE_BOUND) {
            return isDensePresent(key);
        }
        return sparse != null && sparse.containsKey(key);
    }

    public @Nullable E remove(char key) {
        if (key < DENSE_BOUND) {
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

    public void forEachEntry(@NotNull CharEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (int i = 0; i < DENSE_BOUND; i++) {
            if (isDensePresent(i)) {
                action.accept((char) i, dense[i]);
            }
        }
        if (sparse != null && !sparse.isEmpty()) {
            for (var entry : sparse.char2ObjectEntrySet()) {
                action.accept(entry.getCharKey(), entry.getValue());
            }
        }
    }
}
