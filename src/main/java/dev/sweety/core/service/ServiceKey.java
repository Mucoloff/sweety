package dev.sweety.core.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ServiceKey<T> {
    private final Class<T> type;
    private final int hashCode;
    private final String name;

    private ServiceKey(final Class<T> type, final String name) {
        this.type = type;
        this.name = name;
        this.hashCode = Objects.hash(type, name);
    }

    @NotNull
    public static <T> ServiceKey<T> key(@NotNull final Class<T> type) {
        return new ServiceKey<>(type, null);
    }

    @NotNull
    public static <T> ServiceKey<T> key(@NotNull final Class<T> type, @NotNull final String name) {
        return new ServiceKey<>(type, name);
    }

    @NotNull
    public Class<T> getType() {
        return this.type;
    }

    @Nullable
    public String getName() {
        return this.name;
    }

    public boolean equals(final Object obj) {
        if (!(obj instanceof ServiceKey<?>)) return false;
        ServiceKey<?> that = (ServiceKey<?>) obj;
        return this.type == that.type && Objects.equals(this.name, that.name);
    }

    public int hashCode() {
        return this.hashCode;
    }

    public String toString() {
        return this.name == null ? this.type.getName() : this.type.getName() + "(" + this.name + ")";
    }
}