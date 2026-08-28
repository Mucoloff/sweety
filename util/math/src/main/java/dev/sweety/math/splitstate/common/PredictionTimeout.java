package dev.sweety.math.splitstate.common;

import dev.sweety.math.splitstate.mutable.MutablePrediction;

/**
 * Wraps a {@link MutablePrediction} with an age check. If the server hasn't confirmed the
 * prediction within {@code timeoutMillis}, treat it as stale and roll back (e.g. desync
 * from a dropped packet) instead of trusting an unconfirmed value indefinitely.
 */
public class PredictionTimeout<T> {

    private final MutablePrediction<T> prediction;
    private final long timeoutMillis;
    private long predictedAtMillis;

    public PredictionTimeout(MutablePrediction<T> prediction, long timeoutMillis) {
        this.prediction = prediction;
        this.timeoutMillis = timeoutMillis;
        this.predictedAtMillis = System.currentTimeMillis();
    }

    public static <T> PredictionTimeout<T> confirmedOf(T value, long timeoutMillis) {
        return new PredictionTimeout<>(MutablePrediction.confirmedOf(value), timeoutMillis);
    }

    public void predict(T value) {
        prediction.predict(value);
        this.predictedAtMillis = System.currentTimeMillis();
    }

    public void confirm() {
        prediction.confirm();
    }

    public void confirm(T serverValue) {
        prediction.confirm(serverValue);
    }

    /** Rolls back and returns true if the pending prediction expired since it was last set. */
    public boolean tickExpiry() {
        if (!prediction.isPending()) return false;
        if (System.currentTimeMillis() - predictedAtMillis < timeoutMillis) return false;
        prediction.rollback();
        return true;
    }

    public boolean isPending() {
        return prediction.isPending();
    }

    public MutablePrediction<T> prediction() {
        return prediction;
    }
}
