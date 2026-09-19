package dev.sweety.math.map;

import it.unimi.dsi.fastutil.floats.Float2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Primitive-keyed {@code float -> E} enum map backed by {@link Float2ObjectOpenHashMap}.
 */
public final class Float2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface FloatEnumConsumer<E> {
        void accept(float key, E value);
    }

    private final Class<E> valueType;
    private final Float2ObjectOpenHashMap<E> map;

    private Float2EnumMap(@NotNull Class<E> valueType, int initialCapacity) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
        this.map = new Float2ObjectOpenHashMap<>(initialCapacity);
    }

    public static <E extends Enum<E>> Float2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Float2EnumMap<>(valueType, 16);
    }

    public static <E extends Enum<E>> Float2EnumMap<E> of(@NotNull Class<E> valueType, int initialCapacity) {
        return new Float2EnumMap<>(valueType, initialCapacity);
    }

    public Class<E> valueType() {
        return valueType;
    }

    public @Nullable E put(float key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        return map.put(key, value);
    }

    public @Nullable E get(float key) {
        return map.get(key);
    }

    public @Nullable E getOrDefault(float key, @Nullable E defaultValue) {
        E val = map.get(key);
        return val != null ? val : defaultValue;
    }

    public boolean containsKey(float key) {
        return map.containsKey(key);
    }

    public @Nullable E remove(float key) {
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

    public void forEachEntry(@NotNull FloatEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        for (var entry : map.float2ObjectEntrySet()) {
            action.accept(entry.getFloatKey(), entry.getValue());
        }
    }
}
