package dev.sweety.netty.server.discovery;

import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Dynamic DNS-based endpoint provider designed for Kubernetes Headless Services
 * (e.g. {@code service-name.namespace.svc.cluster.local}) or DNS A/AAAA records.
 * <p>
 * Periodically polls DNS resolving all A/AAAA records to discover dynamic backend pods.
 */
public final class DnsEndpointProvider implements EndpointProvider {

    private static final SimpleLogger LOGGER = SimpleLogger.of(DnsEndpointProvider.class);

    private final String hostname;
    private final int port;
    private final long pollIntervalMs;
    private final ScheduledExecutorService scheduler;
    private final List<Consumer<List<InetSocketAddress>>> listeners = new CopyOnWriteArrayList<>();

    private volatile List<InetSocketAddress> currentEndpoints = List.of();
    private ScheduledFuture<?> pollTask;

    public DnsEndpointProvider(@NotNull String hostname, int port, long pollIntervalMs) {
        this.hostname = Objects.requireNonNull(hostname, "hostname cannot be null");
        this.port = port;
        this.pollIntervalMs = Math.max(500, pollIntervalMs);
        this.scheduler = ThreadUtil.singleThreadScheduler("dns-discovery-" + hostname);
        refresh();
        this.pollTask = this.scheduler.scheduleWithFixedDelay(
                this::poll,
                this.pollIntervalMs,
                this.pollIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    public static DnsEndpointProvider of(@NotNull String hostname, int port) {
        return new DnsEndpointProvider(hostname, port, 5000);
    }

    public static DnsEndpointProvider of(@NotNull String hostname, int port, long pollIntervalMs) {
        return new DnsEndpointProvider(hostname, port, pollIntervalMs);
    }

    private void poll() {
        try {
            refresh();
        } catch (Throwable t) {
            LOGGER.error("Failed to poll DNS for " + hostname + ":" + port + ": " + t.getMessage());
        }
    }

    public synchronized void refresh() {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            List<InetSocketAddress> resolved = new ArrayList<>(addresses.length);
            for (InetAddress addr : addresses) {
                resolved.add(new InetSocketAddress(addr, port));
            }
            List<InetSocketAddress> immutableResolved = List.copyOf(resolved);

            if (!endpointsEqual(this.currentEndpoints, immutableResolved)) {
                LOGGER.info("DNS topology changed for " + hostname + ": " + immutableResolved.size() + " endpoints discovered.");
                this.currentEndpoints = immutableResolved;
                for (Consumer<List<InetSocketAddress>> listener : listeners) {
                    try {
                        listener.accept(immutableResolved);
                    } catch (Throwable t) {
                        LOGGER.error("Error in endpoint discovery listener: " + t.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("DNS resolution failed for " + hostname + ": " + e.getMessage());
        }
    }

    private static boolean endpointsEqual(List<InetSocketAddress> a, List<InetSocketAddress> b) {
        if (a.size() != b.size()) return false;
        return a.containsAll(b) && b.containsAll(a);
    }

    @Override
    public List<InetSocketAddress> resolveEndpoints() {
        return this.currentEndpoints;
    }

    @Override
    public void onUpdate(Consumer<List<InetSocketAddress>> listener) {
        if (listener != null) {
            this.listeners.add(listener);
            listener.accept(this.currentEndpoints);
        }
    }

    @Override
    public void close() {
        if (pollTask != null) {
            pollTask.cancel(true);
        }
        scheduler.shutdownNow();
    }
}
