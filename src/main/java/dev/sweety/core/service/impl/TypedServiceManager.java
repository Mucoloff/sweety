package dev.sweety.core.service.impl;

import dev.sweety.core.service.Provider;
import dev.sweety.core.service.ServiceKey;
import dev.sweety.core.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TypedServiceManager<Type> implements ServiceRegistry, AutoCloseable {

    private final ServiceManager internal = new ServiceManager();
    private final Class<Type> baseType;

    public ServiceManager internal() {
        return internal;
    }

    public TypedServiceManager(@NotNull Class<Type> baseType) {
        this.baseType = baseType;
    }

    private void checkType(Object value) {
        if (!baseType.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Service of type " + value.getClass().getName() +
                            " is not assignable to " + baseType.getName()
            );
        }
    }

    private <T> Provider<T> checked(Provider<T> provider) {
        return () -> {
            T value = provider.get();
            checkType(value);
            return value;
        };
    }

    @Override
    public @NotNull Set<ServiceKey<?>> keySet() {
        return internal.keySet();
    }

    @Override
    public @NotNull Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet() {
        return internal.entrySet();
    }

    @Override
    public @NotNull Collection<Provider<?>> providers() {
        return internal.providers();
    }

    public @NotNull Collection<Type> values() {
        return internal.providers().stream()
                .map(Provider::get)
                .filter(baseType::isInstance)
                .map(baseType::cast)
                .collect(Collectors.toList());
    }

    @Override
    public @Nullable <T> T getOrNull(@NotNull ServiceKey<T> key) {
        return internal.getOrNull(key);
    }

    @Override
    public @Nullable <T> T put(@NotNull ServiceKey<T> key, T service) {
        checkType(service);
        return internal.put(key, service);
    }

    @Override
    public @Nullable <T> T put(@NotNull ServiceKey<T> key, Provider<T> service) {
        return internal.put(key, checked(service));
    }

    @Override
    public <T> boolean contains(@NotNull ServiceKey<T> key) {
        return internal.contains(key);
    }

    @Override
    public <T> boolean contains(@NotNull Class<T> type) {
        return internal.contains(type);
    }

    @Override
    public @Nullable <T> T putIfAbsent(@NotNull ServiceKey<T> key, T service) {
        checkType(service);
        return internal.putIfAbsent(key, service);
    }

    @Override
    public @Nullable <T> T putIfAbsent(@NotNull ServiceKey<T> key, Provider<T> service) {
        return internal.putIfAbsent(key, checked(service));
    }

    @Override
    public void close() {
        internal.close();
    }

    @Override
    public @NotNull <T> T get(@NotNull ServiceKey<T> key) {
        return internal.get(key);
    }

    @Override
    public @NotNull <T> T get(@NotNull Class<T> type) {
        return internal.get(type);
    }

    @Override
    public @Nullable <T> T getOrNull(@NotNull Class<T> type) {
        return internal.getOrNull(type);
    }

    @Override
    public @Nullable <T> T put(@NotNull Class<T> type, Provider<T> service) {
        return internal.put(type, checked(service));
    }

    @Override
    public @Nullable <T> T put(@NotNull Class<T> type, T service) {
        checkType(service);
        return internal.put(type, service);
    }

    @Override
    public @Nullable <T> T putIfAbsent(@NotNull Class<T> type, T service) {
        checkType(service);
        return internal.putIfAbsent(type, service);
    }

    @Override
    public @Nullable <T> T putIfAbsent(@NotNull Class<T> type, Provider<T> service) {
        return internal.putIfAbsent(type, checked(service));
    }

    /* ===================== TYPED PUT ===================== */

    public <S extends Type> @Nullable S putTyped(
            @NotNull ServiceKey<S> key,
            @NotNull S service
    ) {
        return put(key, service);
    }

    public <S extends Type> @Nullable S putTyped(
            @NotNull ServiceKey<S> key,
            @NotNull Provider<S> service
    ) {
        return put(key, service);
    }

    /* ===================== TYPED PUT IF ABSENT ===================== */

    public <S extends Type> @Nullable S putIfAbsentTyped(
            @NotNull ServiceKey<S> key,
            @NotNull S service
    ) {
        return putIfAbsent(key, service);
    }

    public <S extends Type> @Nullable S putIfAbsentTyped(
            @NotNull ServiceKey<S> key,
            @NotNull Provider<S> service
    ) {
        return putIfAbsent(key, service);
    }

    /* ===================== TYPED PUT (CLASS) ===================== */

    public <S extends Type> @Nullable S putTyped(
            @NotNull Class<S> type,
            @NotNull S service
    ) {
        return put(type, service);
    }

    public <S extends Type> @Nullable S putTyped(
            @NotNull Class<S> type,
            @NotNull Provider<S> service
    ) {
        return put(type, service);
    }

    /* ===================== TYPED PUT IF ABSENT (CLASS) ===================== */

    public <S extends Type> @Nullable S putIfAbsentTyped(
            @NotNull Class<S> type,
            @NotNull S service
    ) {
        return putIfAbsent(type, service);
    }

    public <S extends Type> @Nullable S putIfAbsentTyped(
            @NotNull Class<S> type,
            @NotNull Provider<S> service
    ) {
        return putIfAbsent(type, service);
    }

}
