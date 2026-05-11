package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.ServiceRegistry;
import dev.sweety.feature.service.api.annotation.Inject;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;

public final class DependencyInjector {

    private DependencyInjector() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> T instantiate(@NotNull ServiceRegistry registry, @NotNull Class<T> type) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        try {
            T instance = createInstance(registry, type);
            injectFields(registry, instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate service: " + type.getName(), e);
        }
    }

    private static <T> T createInstance(ServiceRegistry registry, Class<T> type) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        Constructor<?> injectConstructor = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .findFirst()
                .orElse(null);

        if (injectConstructor == null) {
            // Try default constructor
            try {
                Constructor<T> def = type.getDeclaredConstructor();
                def.setAccessible(true);
                return def.newInstance();
            } catch (NoSuchMethodException e) {
                // Pick first constructor if only one
                if (constructors.length != 1)
                    throw new RuntimeException("No @Inject constructor or default constructor found for " + type.getName());
                injectConstructor = constructors[0];
            }
        }

        injectConstructor.setAccessible(true);
        Object[] params = Arrays.stream(injectConstructor.getParameterTypes())
                .map(registry::get)
                .toArray();

        //noinspection unchecked
        return (T) injectConstructor.newInstance(params);
    }

    public static void injectFields(@NotNull ServiceRegistry registry, @NotNull Object instance) throws IllegalAccessException {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    field.setAccessible(true);
                    Object value = registry.get(field.getType());
                    field.set(instance, value);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
