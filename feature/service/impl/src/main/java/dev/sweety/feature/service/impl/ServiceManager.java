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

        Provider<T>[] old = new Provider[1];
        services.compute(key, (k, prev) -> {
            //noinspection unchecked
            old[0] = (Provider<T>) prev;
            return singleton(service);
        });

        T oldValue = old[0] == null ? null : old[0].get();
        if (oldValue instanceof Service s) s.onDisable();
        if (service instanceof Service s) s.onEnable();
        return oldValue;
    }

    @Override

    public <T> T put(@NotNull final ServiceKey<T> key, final Provider<T> service) {
        ensureOpen();
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(service, "service provider cannot be null");

        Provider<T>[] old = new Provider[1];
        services.compute(key, (k, prev) -> {
            //noinspection unchecked
            old[0] = (Provider<T>) prev;
            return service;
        });

        T oldValue = old[0] == null ? null : old[0].get();
        if (oldValue instanceof Service s) s.onDisable();
        return oldValue;
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
        T instance = DependencyInjector.instantiate(this, type);
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
                } catch (Exception e) {
                    // best-effort shutdown — log and continue disabling remaining services
                    System.getLogger(ServiceManager.class.getName()).log(System.Logger.Level.WARNING, "Service onDisable threw", e);
                }
            }
            if (value instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception e) {
                    System.getLogger(ServiceManager.class.getName()).log(System.Logger.Level.WARNING, "AutoCloseable.close threw during shutdown", e);
                }
            }
        });

        services.clear();
    }
}