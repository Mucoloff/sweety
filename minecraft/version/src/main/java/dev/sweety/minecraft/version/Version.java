package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

public interface Version<T extends Version<T>> extends Comparable<T> {

    int protocolVersion();

    @NotNull
    String releaseName();

    int major();

    int minor();

    int patch();

    /**
     * Returns the specific MinecraftVersion representation.
     */
    @NotNull
    MinecraftVersion specific();

    default boolean isNewerThan(@NotNull Version other) {
        return compareTo(other) > 0;
    }

    default boolean isOlderThan(@NotNull Version other) {
        return compareTo(other) < 0;
    }

    default boolean isNewerThanOrEquals(@NotNull Version other) {
        return compareTo(other) >= 0;
    }

    default boolean isOlderThanOrEquals(@NotNull Version other) {
        return compareTo(other) <= 0;
    }

    default boolean isAtLeast(@NotNull Version other) {
        return isNewerThanOrEquals(other);
    }

    default boolean isBetween(@NotNull Version start, @NotNull Version end) {
        return isNewerThanOrEquals(start) && isOlderThanOrEquals(end);
    }

    int ordinal();

    @Override
    default int compareTo(@NotNull Version o) {
        if (this.major() != o.major()) return Integer.compare(this.major(), o.major());
        if (this.minor() != o.minor()) return Integer.compare(this.minor(), o.minor());
        if (this.patch() != o.patch()) return Integer.compare(this.patch(), o.patch());
        return Integer.compare(this.protocolVersion(), o.protocolVersion());
    }
}
