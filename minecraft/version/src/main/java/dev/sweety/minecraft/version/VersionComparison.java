package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * This enum defines comparison predicates and strategies for Minecraft versions.
 */
public enum VersionComparison implements IntPredicate, Comparator<Version<?>> {

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

    public static final Comparator<Version<?>> RELEASE = Comparator
            .<Version<?>>comparingInt(Version::major)
            .thenComparingInt(Version::minor)
            .thenComparingInt(Version::patch)
            .thenComparingInt(Version::protocolVersion);

    public static final Comparator<Version<?>> PROTOCOL = Comparator
            .comparingInt(Version::protocolVersion);

    public static final Comparator<Version<?>> ORDINAL = Comparator
            .comparingInt(v -> v.specific().ordinal());

    /* --- FIELDS & LOGIC --- */

    private final IntPredicate resultPredicate;
    private final int multiplier;

    VersionComparison(IntPredicate resultPredicate, int multiplier) {
        this.resultPredicate = resultPredicate;
        this.multiplier = multiplier;
    }

    @Override
    public boolean test(int comparisonResult) {
        return resultPredicate.test(comparisonResult);
    }

    public boolean test(@NotNull Version<?> a, @NotNull Version<?> b) {
        Objects.requireNonNull(a, "version a cannot be null");
        Objects.requireNonNull(b, "version b cannot be null");
        return test(RELEASE.compare(a, b));
    }

    public boolean test(@NotNull Version<?> a, @NotNull Version<?> b, @NotNull Comparator<Version<?>> strategy) {
        Objects.requireNonNull(a, "version a cannot be null");
        Objects.requireNonNull(b, "version b cannot be null");
        Objects.requireNonNull(strategy, "strategy cannot be null");
        return test(strategy.compare(a, b));
    }

    @Override
    public int compare(Version<?> a, Version<?> b) {
        Objects.requireNonNull(a, "version a cannot be null");
        Objects.requireNonNull(b, "version b cannot be null");
        return multiplier * RELEASE.compare(a, b);
    }
}
