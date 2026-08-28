package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Provider;
import dev.sweety.feature.service.api.ServiceKey;
import dev.sweety.feature.service.api.ServiceRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TypedServiceManager<T> implements ServiceRegistry, AutoCloseable {

    private final ServiceManager internal = new ServiceManager();
    private final Class<T> baseType;

    public ServiceManager internal() {
        return internal;
    }

    public TypedServiceManager(@NotNull Class<T> baseType) {
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

    private <S> Provider<S> checked(Provider<S> provider) {
        return () -> {
            S value = provider.get();
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
    public Collection<T> values() {
        return internal.providers().stream()
                .map(Provider::get)
                .filter(baseType::isInstance)
                .map(baseType::cast)
                .collect(Collectors.toList());
    }

    @Override
    public <S> S getOrNull(@NotNull ServiceKey<S> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.getOrNull(key);
    }

    @Override
    public <S> S put(@NotNull ServiceKey<S> key, S service) {
        Objects.requireNonNull(key, "key cannot be null");
        checkType(service);
        return internal.put(key, service);
    }

    @Override
    public <S> S put(@NotNull ServiceKey<S> key, Provider<S> service) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        return internal.put(key, checked(service));
    }

    @Override
    public <S> boolean contains(@NotNull ServiceKey<S> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.contains(key);
    }

    @Override
    public <S> S putIfAbsent(@NotNull ServiceKey<S> key, S service) {
        Objects.requireNonNull(key, "key cannot be null");
        checkType(service);
        return internal.putIfAbsent(key, service);
    }

    @Override
    public <S> S putIfAbsent(@NotNull ServiceKey<S> key, Provider<S> service) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        return internal.putIfAbsent(key, checked(service));
    }

    @Override
    @NotNull
    public <S> S registerByClass(@NotNull Class<S> type) {
        Objects.requireNonNull(type, "type cannot be null");
        S instance = internal.registerByClass(type);
        checkType(instance);
        return instance;
    }

    @Override
    public <S> S remove(@NotNull ServiceKey<S> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return internal.remove(key);
    }

    @Override
    @NotNull
    public ServiceRegistry child(@NotNull Predicate<ServiceKey<?>> selector) {
        return new ChildServiceRegistry(this, selector);
    }

    @Override
    public void close() {
        internal.close();
    }

    /* ===================== TYPED PUT ===================== */

    public <S extends T> S putTyped(@NotNull ServiceKey<S> key, @NotNull S service) {
        return put(key, service);
    }

    public <S extends T> S putTyped(@NotNull ServiceKey<S> key, @NotNull Provider<S> service) {
        return put(key, service);
    }

    /* ===================== TYPED PUT IF ABSENT ===================== */

    public <S extends T> S putIfAbsentTyped(@NotNull ServiceKey<S> key, @NotNull S service) {
        return putIfAbsent(key, service);
    }

    public <S extends T> S putIfAbsentTyped(@NotNull ServiceKey<S> key, @NotNull Provider<S> service) {
        return putIfAbsent(key, service);
    }

    /* ===================== TYPED PUT (CLASS) ===================== */

    public <S extends T> S putTyped(@NotNull Class<S> type, @NotNull S service) {
        return put(type, service);
    }

    public <S extends T> S putTyped(@NotNull Class<S> type, @NotNull Provider<S> service) {
        return put(type, service);
    }

    /* ===================== TYPED PUT IF ABSENT (CLASS) ===================== */

    public <S extends T> S putIfAbsentTyped(@NotNull Class<S> type, @NotNull S service) {
        return putIfAbsent(type, service);
    }

    public <S extends T> S putIfAbsentTyped(@NotNull Class<S> type, @NotNull Provider<S> service) {
        return putIfAbsent(type, service);
    }

}
