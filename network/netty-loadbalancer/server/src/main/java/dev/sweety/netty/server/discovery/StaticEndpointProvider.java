package dev.sweety.netty.server.discovery;

import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Static in-memory endpoint provider for pre-configured backend nodes.
 */
public final class StaticEndpointProvider implements EndpointProvider {

    private final List<InetSocketAddress> endpoints;

    public StaticEndpointProvider(@NotNull Collection<InetSocketAddress> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints cannot be null");
        this.endpoints = List.copyOf(endpoints);
    }

    public StaticEndpointProvider(@NotNull InetSocketAddress... endpoints) {
        Objects.requireNonNull(endpoints, "endpoints cannot be null");
        this.endpoints = List.copyOf(Arrays.asList(endpoints));
    }

    public static StaticEndpointProvider of(@NotNull InetSocketAddress... endpoints) {
        return new StaticEndpointProvider(endpoints);
    }

    public static StaticEndpointProvider of(@NotNull Collection<InetSocketAddress> endpoints) {
        return new StaticEndpointProvider(endpoints);
    }

    @Override
    public List<InetSocketAddress> resolveEndpoints() {
        return this.endpoints;
    }

    @Override
    public void onUpdate(Consumer<List<InetSocketAddress>> listener) {
        if (listener != null) {
            listener.accept(this.endpoints);
        }
    }

    @Override
    public void close() {
        // No-op for static endpoints
    }
}
