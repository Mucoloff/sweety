package dev.sweety.math.splitstate.common;

/**
 * Policy for picking which side of a {@link Prediction} to trust (e.g. render position from
 * predicted, hitbox/combat checks from confirmed to stay server-authoritative).
 */
@FunctionalInterface
public interface Resolver<T> {

    T resolve(Prediction<T> prediction);

    static <T> Resolver<T> alwaysPredicted() {
        return Prediction::predicted;
    }

    static <T> Resolver<T> alwaysConfirmed() {
        return Prediction::confirmed;
    }

    /** Trust predicted while pending, fall back to confirmed once {@code timeout} rolls it back. */
    static <T> Resolver<T> timeoutAware(PredictionTimeout<T> timeout) {
        return prediction -> {
            timeout.tickExpiry();
            return prediction.predicted();
        };
    }
}
