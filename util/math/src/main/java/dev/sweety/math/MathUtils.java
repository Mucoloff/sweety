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

    public static float clamp(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    public static double clamp(double value) {
        return Math.clamp(value, 0f, 1d);
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
