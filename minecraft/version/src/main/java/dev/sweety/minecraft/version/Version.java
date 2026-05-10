package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

public interface Version extends Comparable<Version> {

    int protocolVersion();

    @NotNull
    String releaseName();

    default boolean isNewerThan(@NotNull Version other) {
        return this.ordinal() > other.ordinal();
    }

    default boolean isOlderThan(@NotNull Version other) {
        return this.ordinal() < other.ordinal();
    }

    default boolean isNewerThanOrEquals(@NotNull Version other) {
        return this.ordinal() >= other.ordinal();
    }

    default boolean isOlderThanOrEquals(@NotNull Version other) {
        return this.ordinal() <= other.ordinal();
    }

    default boolean isAtLeast(@NotNull Version other) {
        return isNewerThanOrEquals(other);
    }

    default boolean isBetween(@NotNull Version start, @NotNull Version end) {
        return isNewerThanOrEquals(start) && isOlderThanOrEquals(end);
    }

    int ordinal();
}
