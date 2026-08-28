package dev.sweety.math.splitstate.mutable;

import dev.sweety.math.splitstate.common.VersionedValue;
import dev.sweety.math.splitstate.immutable.ValueSnapshot;

/**
 * Mutable {@link VersionedValue}. Self-updates in place instead of allocating.
 */
public class TrackedValue<T> implements VersionedValue<T> {

    private T value;
    private T previous;

    public TrackedValue(T value, T previous) {
        this.value = value;
        this.previous = previous;
    }

    public static <T> TrackedValue<T> of(T value) {
        return new TrackedValue<>(value, null);
    }

    @Override
    public T value() {
        return value;
    }

    @Override
    public T previous() {
        return previous;
    }

    public void update(T value) {
        this.previous = this.value;
        this.value = value;
    }

    public void updateImmediate(T value) {
        this.value = value;
        this.previous = null;
    }

    public void setPreviousImmediate(T previous) {
        this.previous = previous;
    }

    @Override
    public ValueSnapshot<T> toSnapshot() {
        return new ValueSnapshot<>(value, previous);
    }

    @Override
    public TrackedValue<T> toTracked() {
        return this;
    }
}
