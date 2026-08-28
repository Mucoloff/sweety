package dev.sweety.math.splitstate.immutable;

import dev.sweety.math.splitstate.common.Prediction;
import dev.sweety.math.splitstate.mutable.MutablePrediction;

/**
 * Immutable {@link Prediction}. Each transition returns a new instance.
 */
public record PredictionSnapshot<T>(T predicted, T confirmed) implements Prediction<T> {

    public static <T> PredictionSnapshot<T> confirmedOf(T value) {
        return new PredictionSnapshot<>(value, value);
    }

    public PredictionSnapshot<T> predict(T value) {
        return new PredictionSnapshot<>(value, confirmed);
    }

    public PredictionSnapshot<T> confirm() {
        return new PredictionSnapshot<>(confirmed, confirmed);
    }

    public PredictionSnapshot<T> confirm(T serverValue) {
        return new PredictionSnapshot<>(serverValue, serverValue);
    }

    public PredictionSnapshot<T> rollback() {
        return new PredictionSnapshot<>(confirmed, confirmed);
    }

    /** Merge: keep this prediction unless {@code other} is more recent (server wins on conflict). */
    public PredictionSnapshot<T> mergeConfirmed(T serverValue) {
        if (java.util.Objects.equals(serverValue, confirmed)) return this;
        return new PredictionSnapshot<>(predicted, serverValue);
    }

    @Override
    public PredictionSnapshot<T> toSnapshot() {
        return this;
    }

    @Override
    public MutablePrediction<T> toMutable() {
        return new MutablePrediction<>(predicted, confirmed);
    }
}
