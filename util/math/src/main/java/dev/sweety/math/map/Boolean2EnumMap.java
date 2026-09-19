package dev.sweety.math.map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Ultra-fast primitive-keyed {@code boolean -> E} enum map.
 * Since boolean has only two states (false and true), this map uses two slots with zero allocations.
 */
public final class Boolean2EnumMap<E extends Enum<E>> {

    @FunctionalInterface
    public interface BooleanEnumConsumer<E> {
        void accept(boolean key, E value);
    }

    private final Class<E> valueType;
    private E falseVal;
    private E trueVal;
    private boolean falsePresent;
    private boolean truePresent;

    private Boolean2EnumMap(@NotNull Class<E> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType cannot be null");
    }

    public static <E extends Enum<E>> Boolean2EnumMap<E> of(@NotNull Class<E> valueType) {
        return new Boolean2EnumMap<>(valueType);
    }

    public Class<E> valueType() {
        return valueType;
    }

    public @Nullable E put(boolean key, @NotNull E value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (key) {
            E prev = trueVal;
            trueVal = value;
            truePresent = true;
            return prev;
        } else {
            E prev = falseVal;
            falseVal = value;
            falsePresent = true;
            return prev;
        }
    }

    public @Nullable E get(boolean key) {
        return key ? (truePresent ? trueVal : null) : (falsePresent ? falseVal : null);
    }

    public @Nullable E getOrDefault(boolean key, @Nullable E defaultValue) {
        return key ? (truePresent ? trueVal : defaultValue) : (falsePresent ? falseVal : defaultValue);
    }

    public boolean containsKey(boolean key) {
        return key ? truePresent : falsePresent;
    }

    public @Nullable E remove(boolean key) {
        if (key) {
            if (!truePresent) return null;
            E prev = trueVal;
            trueVal = null;
            truePresent = false;
            return prev;
        } else {
            if (!falsePresent) return null;
            E prev = falseVal;
            falseVal = null;
            falsePresent = false;
            return prev;
        }
    }

    public int size() {
        return (falsePresent ? 1 : 0) + (truePresent ? 1 : 0);
    }

    public boolean isEmpty() {
        return !falsePresent && !truePresent;
    }

    public void clear() {
        falseVal = null;
        trueVal = null;
        falsePresent = false;
        truePresent = false;
    }

    public void forEachEntry(@NotNull BooleanEnumConsumer<E> action) {
        Objects.requireNonNull(action, "action cannot be null");
        if (falsePresent) {
            action.accept(false, falseVal);
        }
        if (truePresent) {
            action.accept(true, trueVal);
        }
    }
}
