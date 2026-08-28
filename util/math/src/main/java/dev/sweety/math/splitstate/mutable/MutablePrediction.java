package dev.sweety.math.splitstate.mutable;

import dev.sweety.math.splitstate.common.Prediction;
import dev.sweety.math.splitstate.immutable.PredictionSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Mutable {@link Prediction}. Self-updates in place instead of allocating.
 * Combat/movement hot paths (per-tick position/rotation prediction) should prefer this
 * over {@link PredictionSnapshot} to avoid churn.
 */
public class MutablePrediction<T> implements Prediction<T> {

    private T predicted;
    private T confirmed;

    public MutablePrediction(T predicted, T confirmed) {
        this.predicted = predicted;
        this.confirmed = confirmed;
    }

    public static <T> MutablePrediction<T> confirmedOf(T value) {
        return new MutablePrediction<>(value, value);
    }

    @Override
    public T predicted() {
        return predicted;
    }

    @Override
    public T confirmed() {
        return confirmed;
    }

    public void predict(@NotNull T value) {
        Objects.requireNonNull(value, "predicted value cannot be null");
        this.predicted = value;
    }

    /** Server acknowledged the currently predicted value. */
    public void confirm() {
        this.confirmed = predicted;
    }

    /** Server sent an authoritative value; adopt it as both predicted and confirmed. */
    public void confirm(T serverValue) {
        this.predicted = serverValue;
        this.confirmed = serverValue;
    }

    /** Discard the pending prediction, revert to last confirmed value. */
    public void rollback() {
        this.predicted = confirmed;
    }

    @Override
    public PredictionSnapshot<T> toSnapshot() {
        return new PredictionSnapshot<>(predicted, confirmed);
    }

    @Override
    public MutablePrediction<T> toMutable() {
        return this;
    }
}
