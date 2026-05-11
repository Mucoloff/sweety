package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TypedServiceManager<Type> implements ServiceRegistry, AutoCloseable {

    private final ServiceManager internal = new ServiceManager();
    private final Class<Type> baseType;

    public ServiceManager internal() {
        return internal;
    }

    public TypedServiceManager(@NotNull Class<Type> baseType) {
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
    }

    private void checkType(Object value) {
        if (value != null && !baseType.isInstance(value)) {
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
    @NotNull
    public Set<ServiceKey<?>> keySet() {
        return internal.keySet();
    }

    @Override
    @NotNull
    public Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet() {
        return internal.entrySet();
    }

    @Override
    @NotNull
    public Collection<Provider<?>> providers() {
        return internal.providers();
    }

    @NotNull
    public Collection<Type> values() {
        return internal.providers().stream()
                .map(Provider::get)
                .filter(baseType::isInstance)
                .map(baseType::cast)
                .collect(Collectors.toList());
    }

    @Override
    
    public <T> T getOrNull(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.getOrNull(key);
    }

    @Override
    
    public <T> T put(@NotNull ServiceKey<T> key, T service) {
        Objects.requireNonNull(key, "key cannot be null");
        checkType(service);
        return internal.put(key, service);
    }

    @Override
    
    public <T> T put(@NotNull ServiceKey<T> key, Provider<T> service) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        return internal.put(key, checked(service));
    }

    @Override
    public <T> boolean contains(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.contains(key);
    }

    @Override
    
    public <T> T putIfAbsent(@NotNull ServiceKey<T> key, T service) {
        Objects.requireNonNull(key, "key cannot be null");
        checkType(service);
        return internal.putIfAbsent(key, service);
    }

    @Override
    
    public <T> T putIfAbsent(@NotNull ServiceKey<T> key, Provider<T> service) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        return internal.putIfAbsent(key, checked(service));
    }

    @Override
    @NotNull
    public <T> T registerByClass(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        T instance = internal.registerByClass(type);
        checkType(instance);
        return instance;
    }

    @Override
    
    public <T> T remove(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.remove(key);
    }

    @Override
    public void close() {
        internal.close();
    }

    /* ===================== TYPED PUT ===================== */

    public <S extends Type>  S putTyped(
            @NotNull ServiceKey<S> key,
            @NotNull S service
    ) {
        return put(key, service);
    }

    public <S extends Type>  S putTyped(
            @NotNull ServiceKey<S> key,
            @NotNull Provider<S> service
    ) {
        return put(key, service);
    }

    /* ===================== TYPED PUT IF ABSENT ===================== */

    public <S extends Type>  S putIfAbsentTyped(
            @NotNull ServiceKey<S> key,
            @NotNull S service
    ) {
        return putIfAbsent(key, service);
    }

    public <S extends Type>  S putIfAbsentTyped(
            @NotNull ServiceKey<S> key,
            @NotNull Provider<S> service
    ) {
        return putIfAbsent(key, service);
    }

    /* ===================== TYPED PUT (CLASS) ===================== */

    public <S extends Type>  S putTyped(
            @NotNull Class<S> type,
            @NotNull S service
    ) {
        return put(type, service);
    }

    public <S extends Type>  S putTyped(
            @NotNull Class<S> type,
            @NotNull Provider<S> service
    ) {
        return put(type, service);
    }

    /* ===================== TYPED PUT IF ABSENT (CLASS) ===================== */

    public <S extends Type>  S putIfAbsentTyped(
            @NotNull Class<S> type,
            @NotNull S service
    ) {
        return putIfAbsent(type, service);
    }

    public <S extends Type>  S putIfAbsentTyped(
            @NotNull Class<S> type,
            @NotNull Provider<S> service
    ) {
        return putIfAbsent(type, service);
    }

}
