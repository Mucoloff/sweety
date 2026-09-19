package dev.sweety.math.map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * 100% dense primitive-keyed {@code byte -> E} map.
 * Since a byte has only 256 possible states, all keys map directly to an array
 * indexed by {@code key & 0xFF}, guaranteeing zero allocations, zero hashing, and $O(1)$ operations.
 */
public final class Byte2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface ByteEnumConsumer<E> {
        void accept(byte key, E value);
    }

    private final Class<E> valueType;
    @SuppressWarnings("unchecked")
    private final E[] table = (E[]) new Enum<?>[256];
    private final long[] present = new long[4]; // 4 * 64 = 256 bits
    private int size;

    private Byte2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Byte2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Byte2EnumMap<>(valueType);
    }

    public Class<E> valueType() {
        return valueType;
    }

    private boolean isPresent(int idx) {
        return (present[idx >> 6] & (1L << (idx & 63))) != 0;
    }

    private void markPresent(int idx) {
        present[idx >> 6] |= (1L << (idx & 63));
    }

    private void markAbsent(int idx) {
        present[idx >> 6] &= ~(1L << (idx & 63));
    }

    public @Nullable E put(byte key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        int idx = key & 0xFF;
        E prev = table[idx];
        if (!isPresent(idx)) {
            markPresent(idx);
            size++;
        }
        table[idx] = value;
        return prev;
    }

    public @Nullable E get(byte key) {
        int idx = key & 0xFF;
        return isPresent(idx) ? table[idx] : null;
    }

    public @Nullable E getOrDefault(byte key, @Nullable E defaultValue) {
        int idx = key & 0xFF;
        return isPresent(idx) ? table[idx] : defaultValue;
    }

    public boolean containsKey(byte key) {
        return isPresent(key & 0xFF);
    }

    public @Nullable E remove(byte key) {
        int idx = key & 0xFF;
        if (!isPresent(idx)) return null;
        markAbsent(idx);
        E prev = table[idx];
        table[idx] = null;
        size--;
        return prev;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        if (size == 0) return;
        Arrays.fill(table, null);
        Arrays.fill(present, 0L);
        size = 0;
    }

    public void forEachEntry(@NotNull ByteEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (int idx = 0; idx < 256; idx++) {
            if (isPresent(idx)) {
                action.accept((byte) idx, table[idx]);
            }
        }
    }
}
