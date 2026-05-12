package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.ServiceKey;
import dev.sweety.feature.service.api.ServiceRegistry;
import dev.sweety.feature.service.api.annotation.Inject;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class DependencyInjector {

    private static final ThreadLocal<ArrayDeque<Class<?>>> INSTANTIATION_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final ClassValue<List<Field>> INJECT_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isAnnotationPresent(Inject.class)) {
                        out.add(f);
                    }
                }
            }
            return List.copyOf(out);
        }
    };

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
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate service: " + type.getName(), e);
        }
    }

    private static void beginInstantiation(Class<?> type) {
        ArrayDeque<Class<?>> stack = INSTANTIATION_STACK.get();
        if (stack.contains(type)) {
            StringBuilder sb = new StringBuilder();
            for (Iterator<Class<?>> it = stack.descendingIterator(); it.hasNext(); ) {
                sb.append(it.next().getName()).append(" -> ");
            }
            sb.append(type.getName());
            throw new IllegalStateException("Circular dependency: " + sb);
        }
        stack.push(type);
    }

    private static void endInstantiation() {
        INSTANTIATION_STACK.get().pop();
    }

    private static Object resolveDependency(
            ServiceRegistry registry,
            Class<?> paramType,
            Class<?> forType,
            int index
    ) {
        Object dep = registry.getOrNull(ServiceKey.key(paramType));
        if (dep == null) {
            throw new IllegalStateException(
                    "Missing dependency for " + forType.getName()
                            + ": constructor parameter " + index + " (" + paramType.getName() + ")"
            );
        }
        return dep;
    }

    private static <T> T createInstance(ServiceRegistry registry, Class<T> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        beginInstantiation(type);
        try {
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            Constructor<?> injectConstructor = Arrays.stream(constructors)
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .findFirst()
                    .orElse(null);

            if (injectConstructor == null) {
                try {
                    Constructor<T> def = type.getDeclaredConstructor();
                    def.setAccessible(true);
                    return def.newInstance();
                } catch (NoSuchMethodException e) {
                    if (constructors.length != 1) {
                        throw new RuntimeException(
                                "No @Inject constructor or default constructor found for " + type.getName());
                    }
                    injectConstructor = constructors[0];
                }
            }

            injectConstructor.setAccessible(true);
            Class<?>[] paramTypes = injectConstructor.getParameterTypes();
            Object[] params = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = resolveDependency(registry, paramTypes[i], type, i);
            }

            //noinspection unchecked
            return (T) injectConstructor.newInstance(params);
        } finally {
            endInstantiation();
        }
    }

    public static void injectFields(@NotNull ServiceRegistry registry, @NotNull Object instance)
            throws IllegalAccessException {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        for (Field field : INJECT_FIELDS.get(instance.getClass())) {
            field.setAccessible(true);
            Object value = registry.getOrNull(ServiceKey.key(field.getType()));
            if (value == null) {
                throw new IllegalStateException(
                        "Missing dependency for field " + field.getName()
                                + " (" + field.getType().getName() + ") in " + instance.getClass().getName()
                );
            }
            field.set(instance, value);
        }
    }
}
