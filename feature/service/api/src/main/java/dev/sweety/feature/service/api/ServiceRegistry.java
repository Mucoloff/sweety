package dev.sweety.feature.service.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface ServiceRegistry {


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

    
    <T> T getOrNull(@NotNull ServiceKey<T> key);

    
    default <T> T getOrNull(@NotNull final Class<T> type) {
        return getOrNull(ServiceKey.key(type));
    }

    
    default <T> T put(@NotNull final Class<T> type, final Provider<T> service) {
        return put(ServiceKey.key(type), service);
    }

    
    <T> T put(@NotNull ServiceKey<T> key, Provider<T> service);

    
    default <T> T put(@NotNull final Class<T> type, final T service) {
        return put(ServiceKey.key(type), service);
    }

    
    <T> T put(@NotNull ServiceKey<T> key, T service);

    
    <T> T putIfAbsent(@NotNull ServiceKey<T> key, T service);

    
    default <T> T putIfAbsent(@NotNull final Class<T> type, final T service) {
        return putIfAbsent(ServiceKey.key(type), service);
    }
    
    <T> T putIfAbsent(@NotNull ServiceKey<T> key, Provider<T> service);

    
    default <T> T putIfAbsent(@NotNull final Class<T> type, final Provider<T> service) {
        return putIfAbsent(ServiceKey.key(type), service);
    }

    default <T> RegistrationBuilder<T> register(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        return new RegistrationBuilder<>(this, type);
    }

    @NotNull
    <T> T registerByClass(@NotNull Class<T> type);
    
    <T> T remove(@NotNull ServiceKey<T> key);
    
    default <T> T remove(@NotNull Class<T> type) {
        return remove(ServiceKey.key(type));
    }

    /**
     * Returns a live child view of this registry with layered semantics:
     * <ul>
     *   <li>Keys that match {@code selector} are <em>inherited</em>: reads delegate to
     *       this parent (live, not a snapshot); writes throw {@link IllegalStateException}.</li>
     *   <li>All other keys go into the child's own local layer and are invisible to the parent.</li>
     * </ul>
     */
    @NotNull
    ServiceRegistry child(@NotNull Predicate<ServiceKey<?>> selector);

    /** Convenience overload: inherit the given explicit keys. */
    @NotNull
    default ServiceRegistry child(@NotNull ServiceKey<?>... keys) {
        Set<ServiceKey<?>> set = Set.of(keys);
        return child(set::contains);
    }

    /** Convenience overload: inherit services registered under the given types (unnamed keys only). */
    @NotNull
    default ServiceRegistry child(@NotNull Class<?>... types) {
        Set<ServiceKey<?>> set = Arrays.stream(types)
                .map(ServiceKey::key)
                .collect(Collectors.toUnmodifiableSet());
        return child(set::contains);
    }

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