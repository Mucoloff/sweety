package dev.sweety.feature.service.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ServiceRegistry {

    static ServiceRegistry create() {
        return new dev.sweety.feature.service.impl.ServiceManager();
    }

    static <T> ServiceRegistry typed(Class<T> baseType) {
        return new dev.sweety.feature.service.impl.TypedServiceManager<>(baseType);
    }

    @NotNull
    Set<ServiceKey<?>> keySet();

    @NotNull
    Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet();

    @NotNull
    Collection<Provider<?>> providers();

    @NotNull
    default <T> T get(@NotNull final ServiceKey<T> key) {
        final T service = getOrNull(key);
        if (service == null) throw new NullPointerException("Service not found: " + key);
        return service;
    }

    @NotNull
    default <T> T get(@NotNull final Class<T> type) {
        return get(ServiceKey.key(type));
    }

    <T> boolean contains(@NotNull ServiceKey<T> key);

    default <T> boolean contains(@NotNull final Class<T> type) {
        return contains(ServiceKey.key(type));
    }

    @Nullable
    <T> T getOrNull(@NotNull ServiceKey<T> key);

    @Nullable
    default <T> T getOrNull(@NotNull final Class<T> type) {
        return getOrNull(ServiceKey.key(type));
    }

    @Nullable
    default <T> T put(@NotNull final Class<T> type, final Provider<T> service) {
        return put(ServiceKey.key(type), service);
    }

    @Nullable
    <T> T put(@NotNull ServiceKey<T> key, Provider<T> service);

    @Nullable
    default <T> T put(@NotNull final Class<T> type, final T service) {
        return put(ServiceKey.key(type), service);
    }

    @Nullable
    <T> T put(@NotNull ServiceKey<T> key, T service);

    @Nullable
    <T> T putIfAbsent(@NotNull ServiceKey<T> key, T service);

    @Nullable
    default <T> T putIfAbsent(@NotNull final Class<T> type, final T service) {
        return putIfAbsent(ServiceKey.key(type), service);
    }

    @Nullable
    <T> T putIfAbsent(@NotNull ServiceKey<T> key, Provider<T> service);

    @Nullable
    default <T> T putIfAbsent(@NotNull final Class<T> type, final Provider<T> service) {
        return putIfAbsent(ServiceKey.key(type), service);
    }

    default <T> RegistrationBuilder<T> register(Class<T> type) {
        return new RegistrationBuilder<>(this, type);
    }

    <T> T registerByClass(Class<T> type);

    class RegistrationBuilder<T> {
        private final ServiceRegistry registry;
        private final Class<T> type;
        private String name;

        public RegistrationBuilder(ServiceRegistry registry, Class<T> type) {
            this.registry = registry;
            this.type = type;
        }

        public RegistrationBuilder<T> named(String name) {
            this.name = name;
            return this;
        }

        public T with(T service) {
            return registry.put(ServiceKey.key(type, name), service);
        }

        public T with(Provider<T> provider) {
            return registry.put(ServiceKey.key(type, name), provider);
        }

        public T ifAbsent(T service) {
            return registry.putIfAbsent(ServiceKey.key(type, name), service);
        }

        public T ifAbsent(Provider<T> provider) {
            return registry.putIfAbsent(ServiceKey.key(type, name), provider);
        }
    }
}