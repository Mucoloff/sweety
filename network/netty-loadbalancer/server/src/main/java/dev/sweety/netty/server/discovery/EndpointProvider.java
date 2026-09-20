package dev.sweety.netty.server.discovery;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;

/**
 * Pluggable endpoint resolution and dynamic discovery provider for backend pools.
 * Supports static endpoints, DNS lookup (Kubernetes Headless Services), and custom service registries.
 */
public interface EndpointProvider extends AutoCloseable {

    /**
     * Resolves the current snapshot of healthy backend endpoint socket addresses.
     */
    List<InetSocketAddress> resolveEndpoints();

    /**
     * Registers a callback invoked whenever the backend endpoint topology changes.
     */
    default void onUpdate(Consumer<List<InetSocketAddress>> listener) {}

    @Override
    default void close() {}
}
