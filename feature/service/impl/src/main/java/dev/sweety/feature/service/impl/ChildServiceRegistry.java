package dev.sweety.feature.service.impl;

import dev.sweety.feature.service.api.Provider;
import dev.sweety.feature.service.api.ServiceKey;
import dev.sweety.feature.service.api.ServiceRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A layered child view of a parent {@link ServiceRegistry}.
 *
 * <ul>
 *   <li>Keys matched by {@code selector} are <em>inherited</em>: reads delegate live to the
 *       parent; write operations ({@code put}, {@code putIfAbsent}, {@code remove},
 *       {@code registerByClass}) throw {@link IllegalStateException} — the child is not owner.</li>
 *   <li>All other keys are owned by this child and stored in a local {@link ServiceManager}
 *       layer. The parent never sees them.</li>
 * </ul>
 *
 * Closing this child only closes its own local layer; the parent is unaffected.
 */
public class ChildServiceRegistry implements ServiceRegistry, AutoCloseable {

    private final ServiceRegistry parent;
    private final Predicate<ServiceKey<?>> selector;
    private final ServiceManager own = new ServiceManager();

    public ChildServiceRegistry(@NotNull ServiceRegistry parent,
                                @NotNull Predicate<ServiceKey<?>> selector) {
        this.parent = Objects.requireNonNull(parent, "parent cannot be null");
        this.selector = Objects.requireNonNull(selector, "selector cannot be null");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean inherited(ServiceKey<?> key) {
        return selector.test(key);
    }

    private void guardWrite(ServiceKey<?> key) {
        if (inherited(key)) {
            throw new IllegalStateException(
                    "Not owner of key " + key + ": inherited from parent. " +
                    "Cannot write through a child view.");
        }
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Override
    public <T> T getOrNull(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        if (own.contains(key))      return own.getOrNull(key);
        if (inherited(key))         return parent.getOrNull(key);
        return null;
    }

    @Override
    public <T> boolean contains(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return own.contains(key) || (inherited(key) && parent.contains(key));
    }

    @Override
    @NotNull
    public Set<ServiceKey<?>> keySet() {
        Set<ServiceKey<?>> result = new HashSet<>(own.keySet());
        parent.keySet().stream().filter(this::inherited).forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    @NotNull
    public Set<Map.Entry<ServiceKey<?>, Provider<?>>> entrySet() {
        Set<Map.Entry<ServiceKey<?>, Provider<?>>> result = new HashSet<>(own.entrySet());
        parent.entrySet().stream()
                .filter(e -> inherited(e.getKey()))
                .forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    @NotNull
    public Collection<Provider<?>> providers() {
        return Stream.concat(
                own.providers().stream(),
                parent.entrySet().stream()
                        .filter(e -> inherited(e.getKey()))
                        .map(Map.Entry::getValue)
        ).collect(Collectors.toList());
    }

    // ── write (own layer only) ────────────────────────────────────────────────

    @Override
    public <T> T put(@NotNull ServiceKey<T> key, T service) {
        Objects.requireNonNull(key, "key cannot be null");
        guardWrite(key);
        return own.put(key, service);
    }

    @Override
    public <T> T put(@NotNull ServiceKey<T> key, Provider<T> service) {
        Objects.requireNonNull(key, "key cannot be null");
        guardWrite(key);
        return own.put(key, service);
    }

    @Override
    public <T> T putIfAbsent(@NotNull ServiceKey<T> key, T service) {
        Objects.requireNonNull(key, "key cannot be null");
        guardWrite(key);
        return own.putIfAbsent(key, service);
    }

    @Override
    public <T> T putIfAbsent(@NotNull ServiceKey<T> key, Provider<T> service) {
        Objects.requireNonNull(key, "key cannot be null");
        guardWrite(key);
        return own.putIfAbsent(key, service);
    }

    @Override
    public <T> T remove(@NotNull ServiceKey<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        guardWrite(key);
        return own.remove(key);
    }

    @Override
    @NotNull
    public <T> T registerByClass(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        guardWrite(ServiceKey.key(type));
        // DI resolves against this child (union view → sees inherited + own)
        T instance = DependencyInjector.instantiate(this, type);
        own.put(type, instance);
        return instance;
    }

    // ── nesting ───────────────────────────────────────────────────────────────

    @Override
    @NotNull
    public ServiceRegistry child(@NotNull java.util.function.Predicate<ServiceKey<?>> childSelector) {
        return new ChildServiceRegistry(this, childSelector);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void close() {
        own.close(); // never touches parent
    }
}
