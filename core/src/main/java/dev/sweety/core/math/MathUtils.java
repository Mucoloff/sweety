package dev.sweety.core.math;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

@UtilityClass
public class MathUtils {

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

    public static float sigmoid(float val) {
        return 1f / (1f + (float)Math.exp(-val));
    }

    public static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public interface Compare<T> {
        boolean compare(T a, T b);
    }

    public static <T> Stream<T> parallel(Collection<T> collection) {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 1) return collection.stream();
        int threshold = 1024;
        if (collection.size() < threshold) return collection.stream();
        return collection.parallelStream();
    }

}
