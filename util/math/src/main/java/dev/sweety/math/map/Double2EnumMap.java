package dev.sweety.math.map;

import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Primitive-keyed {@code double -> E} enum map backed by {@link Double2ObjectOpenHashMap}.
 */
public final class Double2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface DoubleEnumConsumer<E> {
        void accept(double key, E value);
    }

    private final Class<E> valueType;
    private final Double2ObjectOpenHashMap<E> map;

    private Double2EnumMap(@NotNull Class<E> valueType, int initialCapacity) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
        this.map = new Double2ObjectOpenHashMap<>(initialCapacity);
    }

    public static <E extends Enum<E>> Double2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Double2EnumMap<>(valueType, 16);
    }

    public static <E extends Enum<E>> Double2EnumMap<E> of(@NotNull Class<E> valueType, int initialCapacity) {
        return new Double2EnumMap<>(valueType, initialCapacity);
    }

    public Class<E> valueType() {
        return valueType;
    }

    public @Nullable E put(double key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        return map.put(key, value);
    }

    public @Nullable E get(double key) {
        return map.get(key);
    }

    public @Nullable E getOrDefault(double key, @Nullable E defaultValue) {
        E val = map.get(key);
        return val != null ? val : defaultValue;
    }

    public boolean containsKey(double key) {
        return map.containsKey(key);
    }

    public @Nullable E remove(double key) {
        return map.remove(key);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
    }

    public void forEachEntry(@NotNull DoubleEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (var entry : map.double2ObjectEntrySet()) {
            action.accept(entry.getDoubleKey(), entry.getValue());
        }
    }
}
