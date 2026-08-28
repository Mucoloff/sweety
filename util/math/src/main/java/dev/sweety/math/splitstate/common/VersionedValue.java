package dev.sweety.math.splitstate.common;

import dev.sweety.math.splitstate.immutable.ValueSnapshot;
import dev.sweety.math.splitstate.mutable.TrackedValue;

/**
 * Value paired with the value it held one step ago (e.g. previous tick).
 * Not confirmation-aware: use {@link Prediction} for client/server disagreement.
 */
public interface VersionedValue<T> {

    T value();

    T previous();

    default boolean hasPrevious() {
        return previous() != null;
    }

    default boolean changed() {
        return !java.util.Objects.equals(value(), previous());
    }

    ValueSnapshot<T> toSnapshot();

    TrackedValue<T> toTracked();
}
