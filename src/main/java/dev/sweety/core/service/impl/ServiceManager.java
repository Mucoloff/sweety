package dev.sweety.core.service.impl;

import dev.sweety.core.service.Provider;
import dev.sweety.core.service.ServiceKey;
import dev.sweety.core.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
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

    @NotNull
    public Set<ServiceKey<?>> keySet() {
        ensureOpen();
        return this.services.keySet();
    }

    @NotNull
    public Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet() {
        ensureOpen();
        return this.services.entrySet();
    }

    @Override
    public @NotNull Collection<Provider<?>> providers() {
        ensureOpen();
        return this.services.values();
    }

    public @NotNull Collection<?> values() {
        return this.providers().stream().map(Provider::get).collect(Collectors.toList());
    }

    @Nullable
    public <T> T getOrNull(@NotNull final ServiceKey<T> key) {
        ensureOpen();
        // noinspection unchecked
        final Provider<T> provider = (Provider<T>) this.services.get(key);
        return provider == null ? null : provider.get();
    }

    @Override
    public <T> boolean contains(@NotNull ServiceKey<T> key) {
        ensureOpen();
        return services.containsKey(key);
    }

    @Nullable
    public <T> T put(@NotNull final ServiceKey<T> key, final T service) {
        ensureOpen();
        return put(key, singleton(service));
    }

    @Nullable
    public <T> T put(@NotNull final ServiceKey<T> key, final Provider<T> service) {
        ensureOpen();
        // noinspection unchecked
        final Provider<T> provider = (Provider<T>) services.put(key, service);
        return provider == null ? null : provider.get();
    }

    @Nullable
    public <T> T putIfAbsent(@NotNull final ServiceKey<T> type, final T service) {
        ensureOpen();
        return putIfAbsent(type, singleton(service));
    }

    @Nullable
    public <T> T putIfAbsent(@NotNull final ServiceKey<T> key, final Provider<T> service) {
        ensureOpen();
        // noinspection unchecked
        Provider<T> provider = (Provider<T>) services.putIfAbsent(key, service);
        return provider == null ? null : provider.get();
    }

    @Override
    public void close() {
        if (closed.get()) return;
        closed.set(true);

        services.values().forEach(provider -> {
            Object value = provider.get();
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