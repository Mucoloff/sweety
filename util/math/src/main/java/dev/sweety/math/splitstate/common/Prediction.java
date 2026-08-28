package dev.sweety.math.splitstate.common;

import dev.sweety.math.splitstate.immutable.PredictionSnapshot;
import dev.sweety.math.splitstate.mutable.MutablePrediction;

/**
 * Client-predicted value pending server confirmation (position, rotation, sprint/sneak,
 * selected slot, ...). {@code predicted()} is what the client currently believes;
 * {@code confirmed()} is the last value the server acknowledged.
 */
public interface Prediction<T> {

    T predicted();

    T confirmed();

    default boolean isPending() {
        return !java.util.Objects.equals(predicted(), confirmed());
    }

    PredictionSnapshot<T> toSnapshot();

    MutablePrediction<T> toMutable();
}
