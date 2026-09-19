package dev.sweety.math.map;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance primitive-keyed {@code short -> E} enum map.
 */
public final class Short2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface ShortEnumConsumer<E> {
        void accept(short key, E value);
    }

    private static final int DENSE_BOUND = 256;

    private final Class<E> valueType;
    @SuppressWarnings("unchecked")
    private final E[] dense = (E[]) new Enum<?>[DENSE_BOUND];
    private final long[] densePresent = new long[DENSE_BOUND >> 6];

    private Short2ObjectOpenHashMap<E> sparse;
    private int size;

    private Short2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Short2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Short2EnumMap<>(valueType);
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

    public @Nullable E put(short key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        if ((key & ~0xFF) == 0) {
            int idx = key & 0xFF;
            E prev = dense[idx];
            if (!isDensePresent(idx)) {
                markDensePresent(idx);
                size++;
            }
            dense[idx] = value;
            return prev;
        }
        if (sparse == null) {
            sparse = new Short2ObjectOpenHashMap<>();
        }
        E prev = sparse.put(key, value);
        if (prev == null) {
            size++;
        }
        return prev;
    }

    public @Nullable E get(short key) {
        if ((key & ~0xFF) == 0) {
            int idx = key & 0xFF;
            return isDensePresent(idx) ? dense[idx] : null;
        }
        return sparse != null ? sparse.get(key) : null;
    }

    public @Nullable E getOrDefault(short key, @Nullable E defaultValue) {
        if ((key & ~0xFF) == 0) {
            int idx = key & 0xFF;
            return isDensePresent(idx) ? dense[idx] : defaultValue;
        }
        if (sparse != null) {
            E val = sparse.get(key);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    public boolean containsKey(short key) {
        if ((key & ~0xFF) == 0) {
            return isDensePresent(key & 0xFF);
        }
        return sparse != null && sparse.containsKey(key);
    }

    public @Nullable E remove(short key) {
        if ((key & ~0xFF) == 0) {
            int idx = key & 0xFF;
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

    public void forEachEntry(@NotNull ShortEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (int i = 0; i < DENSE_BOUND; i++) {
            if (isDensePresent(i)) {
                action.accept((short) i, dense[i]);
            }
        }
        if (sparse != null && !sparse.isEmpty()) {
            for (var entry : sparse.short2ObjectEntrySet()) {
                action.accept(entry.getShortKey(), entry.getValue());
            }
        }
    }
}
