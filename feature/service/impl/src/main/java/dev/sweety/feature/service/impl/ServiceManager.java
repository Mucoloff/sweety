package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ServiceManager implements ServiceRegistry, AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final Map<ServiceKey<?>, Provider<?>> services = new ConcurrentHashMap<>();
    private final DependencyInjector injector = new DependencyInjector(this);

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("ServiceManager is closed");
    }

    private static <T> Provider<T> singleton(final T service) {
        return () -> service;
    }

    public Map<ServiceKey<?>, Provider<?>> services() {
        return services;
    }

    @Override
    @NotNull
    public Set<ServiceKey<?>> keySet() {
        ensureOpen();
        return this.services.keySet();
    }

    @Override
    @NotNull
    public Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet() {
        ensureOpen();
        return this.services.entrySet();
    }

    @Override
    @NotNull
    public Collection<Provider<?>> providers() {
        ensureOpen();
        return this.services.values();
    }

    @NotNull
    public Collection<?> values() {
        return this.providers().stream().map(Provider::get).collect(Collectors.toList());
    }

    @Override
    
    public <T> T getOrNull(@NotNull final ServiceKey<T> key) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        // noinspection unchecked
        final Provider<T> provider = (Provider<T>) this.services.get(key);
        return provider == null ? null : provider.get();
    }

    @Override
    public <T> boolean contains(@NotNull ServiceKey<T> key) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        return services.containsKey(key);
    }

    @Override
    
    public <T> T put(@NotNull final ServiceKey<T> key, final T service) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service cannot be null");
        if (service instanceof Service s) s.onEnable();
        return put(key, singleton(service));
    }

    @Override
    
    public <T> T put(@NotNull final ServiceKey<T> key, final Provider<T> service) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        // noinspection unchecked
        final Provider<T> provider = (Provider<T>) services.put(key, service);
        T value = provider == null ? null : provider.get();
        if (value instanceof Service s) s.onDisable();
        return value;
    }

    @Override
    
    public <T> T putIfAbsent(@NotNull final ServiceKey<T> key, final T service) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service cannot be null");
        return putIfAbsent(key, singleton(service));
    }

    @Override
    
    public <T> T putIfAbsent(@NotNull final ServiceKey<T> key, final Provider<T> service) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");
        // noinspection unchecked
        Provider<T> provider = (Provider<T>) services.putIfAbsent(key, service);
        return provider == null ? null : provider.get();
    }

    @Override
    @NotNull
    public <T> T registerByClass(@NotNull Class<T> type) {
        ensureOpen();
        Objects.requireNonNull(type, "type cannot be null");
        T instance = injector.instantiate(type);
        put(type, instance);
        return instance;
    }

    @Override
    
    public <T> T remove(@NotNull ServiceKey<T> key) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        // noinspection unchecked
        Provider<T> provider = (Provider<T>) services.remove(key);
        T value = provider == null ? null : provider.get();
        if (value instanceof Service s) s.onDisable();
        return value;
    }

    @Override
    public void close() {
        if (closed.get()) return;
        closed.set(true);

        services.values().forEach(provider -> {
            Object value = provider.get();
            if (value instanceof Service s) {
                try {
                    s.onDisable();
                } catch (Exception ignored) {}
            }
            if (value instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) value).close();
                } catch (Exception ignored) {
                }
            }
        });

        services.clear();
    }
}