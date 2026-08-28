package dev.sweety.math.splitstate.immutable;

import dev.sweety.math.splitstate.common.VersionedValue;
import dev.sweety.math.splitstate.mutable.TrackedValue;

/**
 * Immutable {@link VersionedValue}. Each update returns a new instance.
 */
public record ValueSnapshot<T>(T value, T previous) implements VersionedValue<T> {

    public static <T> ValueSnapshot<T> of(T value) {
        return new ValueSnapshot<>(value, null);
    }

    public ValueSnapshot<T> update(T value) {
        return new ValueSnapshot<>(value, this.value);
    }

    public ValueSnapshot<T> updateImmediate(T value) {
        return new ValueSnapshot<>(value, null);
    }

    public ValueSnapshot<T> withPrevious(T previous) {
        return new ValueSnapshot<>(value, previous);
    }

    @Override
    public ValueSnapshot<T> toSnapshot() {
        return this;
    }

    @Override
    public TrackedValue<T> toTracked() {
        return new TrackedValue<>(value, previous);
    }
}
