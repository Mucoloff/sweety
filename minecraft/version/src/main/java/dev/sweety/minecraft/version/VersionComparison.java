package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiPredicate;

/**
 * This enum contains all possible comparison types for versions.
 */
public enum VersionComparison {
    /*
     * The version equals the compared version.
     */
    EQUALS(Integer::equals),

    /*
     * The version is newer than the compared version.
     */
    NEWER_THAN((a, b) -> a > b),

    /*
     * The version is older than the compared version.
     */
    OLDER_THAN((a, b) -> a < b),

    /*
     * The version is newer than or equal to the compared version.
     */
    NEWER_THAN_OR_EQUALS(OLDER_THAN),

    /*
     * The version is older than or equal to the compared version.
     */
    OLDER_THAN_OR_EQUALS(NEWER_THAN);

    public static final VersionComparison[] VALUES = values();

    private final BiPredicate<Integer, Integer> comparator;

    VersionComparison(BiPredicate<Integer, Integer> comparator) {
        this.comparator = comparator;
    }

    VersionComparison(VersionComparison opposite) {
        this.comparator = (a, b) -> !opposite.comparator.test(a, b);
    }

    public boolean compareByProtocol(@NotNull Version a, @NotNull Version b) {
        return comparator.test(a.protocolVersion(), b.protocolVersion());
    }

    public boolean compareByOrdinal(@NotNull Version a, @NotNull Version b) {
        return comparator.test(a.ordinal(), b.ordinal());
    }
}
