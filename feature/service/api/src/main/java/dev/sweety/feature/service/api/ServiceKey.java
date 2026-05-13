package dev.sweety.feature.service.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record ServiceKey<T>(@NotNull Class<T> type, @Nullable String name) {

    public ServiceKey {
        Objects.requireNonNull(type, "type cannot be null");
    }

    private static final ClassValue<ServiceKey<?>> UNNAMED = new ClassValue<>() {
        @Override
        protected ServiceKey<?> computeValue(Class<?> type) {
            return new ServiceKey<>(type, null);
        }
    };

    @NotNull
    public static <T> ServiceKey<T> key(@NotNull final Class<T> type) {
        //noinspection unchecked
        return (ServiceKey<T>) UNNAMED.get(type);
    }

    @NotNull
    public static <T> ServiceKey<T> key(@NotNull final Class<T> type, @NotNull final String name) {
        return new ServiceKey<>(type, name);
    }

    @Override
    public String toString() {
        return name == null ? type.getName() : type.getName() + "(" + name + ")";
    }
}