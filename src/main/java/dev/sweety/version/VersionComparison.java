package dev.sweety.version;

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
     * The version is newer than or equal to the compared version.
     */
    NEWER_THAN_OR_EQUALS((a, b) -> a >= b),

    /*
     * The version is older than the compared version.
     */
    OLDER_THAN((a, b) -> a < b),

    /*
     * The version is older than or equal to the compared version.
     */
    OLDER_THAN_OR_EQUALS((a, b) -> a <= b);

    public static final VersionComparison[] VALUES = values();

    private final BiPredicate<Integer, Integer> comparator;

    VersionComparison(BiPredicate<Integer, Integer> comparator) {
        this.comparator = comparator;
    }

    public boolean compareByProtocol(@NotNull MinecraftVersion a, @NotNull MinecraftVersion b) {
        return comparator.test(a.getProtocolVersion(), b.getProtocolVersion());
    }

    public boolean compareByOrdinal(@NotNull MinecraftVersion a, @NotNull MinecraftVersion b) {
        return comparator.test(a.ordinal(), b.ordinal());
    }
}
