package dev.sweety.math;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class MathUtils {

    public static <T> T findBest(Collection<T> values, Predicate<T> filter, Compare<T> comparator, T fallback) {
        T best = null;
        for (T t : values) {
            if (!filter.test(t)) continue;
            if (best == null || comparator.compare(t, best)) best = t;
        }
        return best != null ? best : fallback;
    }

    public static <T> T findBest(Collection<T> values, Compare<T> comparator, T fallback) {
        return findBest(values, t -> true, comparator, fallback);
    }

    public static float sigmoid(float value) {
        return 1f / (1f + (float) Math.exp(-value));
    }

    public static double sigmoid(double value) {
        return 1f / (1d + Math.exp(-value));
    }

    /** Clamps to the unit range {@code [0, 1]} — NOT an identity-outside-range clamp; every current
     *  caller uses it for a fraction/percentage (health%, alpha, gaussian noise 0.5±0.5, ...). For an
     *  arbitrary {@code [min, max]} range use {@link Math#clamp(float, float, float)} directly. */
    public static float clamp(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    /** @see #clamp(float) */
    public static double clamp(double value) {
        return Math.clamp(value, 0d, 1d);
    }

    /** Linear interpolation: {@code cur + (target - cur) * frac}. frac=0 → cur, frac=1 → target. */
    public static float lerp(float cur, float target, float frac) {
        return cur + (target - cur) * frac;
    }

    public static double lerp(double cur, double target, double frac) {
        return cur + (target - cur) * frac;
    }

    public static float sin(float value) {
        return (float) Math.sin(value);
    }

    public static float cos(float value) {
        return (float) Math.cos(value);
    }

    public static float distance(float... values) {
        float sum = 0;
        for (float v : values) sum += v * v;
        return (float) Math.sqrt(sum);
    }

    public static double distance(double... values) {
        double sum = 0;
        for (double v : values) sum += v * v;
        return Math.sqrt(sum);
    }

    public static double avg(double... x) {
        double n = 0;
        for (double y : x) n += y;
        return n / x.length;

    }

    public static float avg(float... x) {
        float n = 0;
        for (float y : x) n += y;
        return n / x.length;

    }

    public interface Compare<T> {
        boolean compare(T a, T b);

        static <T> Compare<T> min(Comparator<T> comparator) {
            return (a, b) -> comparator.compare(a, b) < 0;
        }

        static <T> Compare<T> max(Comparator<T> comparator) {
            return (a, b) -> comparator.compare(a, b) > 0;
        }

    }

    public static <T> Stream<T> parallel(Collection<T> collection) {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 1) return collection.stream();
        int threshold = 1024;
        if (collection.size() < threshold) return collection.stream();
        return collection.parallelStream();
    }

}
