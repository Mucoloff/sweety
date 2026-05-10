package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * This enum defines comparison predicates and strategies for Minecraft versions.
 */
public enum VersionComparison implements Predicate<Integer>, Comparator<Version> {

    /**
     * == (Equality)
     */
    EQUALS(res -> res == 0, 1),

    /**
     * != (Inequality)
     */
    NOT_EQUALS(res -> res != 0, 1),

    /**
     * > (Greater Than)
     */
    NEWER_THAN(res -> res > 0, 1),

    /**
     * < (Less Than)
     */
    OLDER_THAN(res -> res < 0, -1),

    /**
     * >= (Greater Than or Equals)
     */
    NEWER_THAN_OR_EQUALS(res -> res >= 0, 1),

    /**
     * <= (Less Than or Equals)
     */
    OLDER_THAN_OR_EQUALS(res -> res <= 0, -1);

    /* --- STATIC STRATEGIES (Using Comparator Factories) --- */

    public static final Comparator<Version> RELEASE = Comparator
            .comparingInt(Version::major)
            .thenComparingInt(Version::minor)
            .thenComparingInt(Version::patch)
            .thenComparingInt(Version::protocolVersion);

    public static final Comparator<Version> PROTOCOL = Comparator
            .comparingInt(Version::protocolVersion);

    public static final Comparator<Version> ORDINAL = Comparator
            .comparingInt(v -> v.specific().ordinal());

    /* --- FIELDS & LOGIC --- */

    private final Predicate<Integer> resultPredicate;
    private final int multiplier;

    VersionComparison(Predicate<Integer> resultPredicate, int multiplier) {
        this.resultPredicate = resultPredicate;
        this.multiplier = multiplier;
    }

    @Override
    public boolean test(Integer comparisonResult) {
        return resultPredicate.test(comparisonResult);
    }

    /**
     * Evaluates if the relationship between two versions satisfies this comparison type
     * using the default RELEASE strategy.
     */
    public boolean test(@NotNull Version a, @NotNull Version b) {
        return test(RELEASE.compare(a, b));
    }

    /**
     * Evaluates the relationship using a specific strategy.
     */
    public boolean test(@NotNull Version a, @NotNull Version b, @NotNull Comparator<Version> strategy) {
        return test(strategy.compare(a, b));
    }

    /**
     * Standard Comparator implementation that respects the direction of the comparison type.
     */
    @Override
    public int compare(Version a, Version b) {
        return multiplier * RELEASE.compare(a, b);
    }
}
