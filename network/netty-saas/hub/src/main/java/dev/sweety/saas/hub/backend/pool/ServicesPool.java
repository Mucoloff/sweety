package dev.sweety.saas.hub.backend.pool;

import dev.sweety.netty.server.LoadBalancerServer;
import dev.sweety.netty.server.balancer.Balancers;
import dev.sweety.netty.server.balancer.CounterBalancer;
import dev.sweety.netty.server.pool.DynamicBackendNodePool;
import dev.sweety.saas.hub.ServiceHub;
import dev.sweety.saas.hub.backend.ServiceNode;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.saas.service.config.ServiceNodeConfig;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServicesPool extends DynamicBackendNodePool<ServiceNode> {

    private final Map<ServiceType, ServiceNode> byType = new ConcurrentHashMap<>();

    /** wall-clock ms of when each type last connected — used by /api/health */
    private final Map<ServiceType, Long> connectedAt = new ConcurrentHashMap<>();

    public ServicesPool(CounterBalancer balancer) {
        super(balancer);
    }

    public ServicesPool(Balancers balancer) {
        super(balancer);
    }

    // ── pool lifecycle ─────────────────────────────────────────────────────

    @Override
    public ServiceNode createAndAdd(LoadBalancerServer<ServiceNode> self,
                                    ChannelHandlerContext ctx,
                                    InetSocketAddress address) {
        final ServiceNode node = super.createAndAdd(self, ctx, address);
        if (node.type() != null) {
            this.byType.put(node.type(), node);
            this.connectedAt.put(node.type(), System.currentTimeMillis());
        }
        return node;
    }

    @Override
    public synchronized ServiceNode remove(final ChannelHandlerContext ctx) {
        final ServiceNode removed = super.remove(ctx);
        if (removed != null && removed.type() != null) {
            this.byType.remove(removed.type(), removed);
            this.connectedAt.remove(removed.type());
        }
        return removed;
    }

    @Override
    public synchronized ChannelHandlerContext remove(final ServiceNode node) {
        if (node.type() != null) {
            this.byType.remove(node.type(), node);
            this.connectedAt.remove(node.type());
        }
        return super.remove(node);
    }

    public synchronized ServiceNode remove(ServiceType type) {
        final ServiceNode node = this.byType.remove(type);
        this.connectedAt.remove(type);
        if (node != null)
            this.remove(node);
        return node;
    }

    /** Promote a placeholder node (null type) to a known type after self-identification. */
    public synchronized void promoteNode(ServiceType type, ServiceNode node) {
        this.byType.put(type, node);
        this.connectedAt.put(type, System.currentTimeMillis());
    }

    // ── lookup ──────────────────────────────────────────────────────────────

    public ServiceNode get(ServiceType type) {
        return this.byType.get(type);
    }

    /** True only if the service has an active, writable channel. */
    public boolean isConnected(ServiceType type) {
        final ServiceNode node = this.byType.get(type);
        if (node == null)
            return false;
        final ChannelHandlerContext ctx = node.context();
        return ctx != null && ctx.channel().isActive();
    }

    /** Returns all ServiceTypes that currently have an active connection. */
    public Set<ServiceType> connectedTypes() {
        final Set<ServiceType> result = new HashSet<>();
        for (final Map.Entry<ServiceType, ServiceNode> entry : byType.entrySet()) {
            final ChannelHandlerContext ctx = entry.getValue().context();
            if (ctx != null && ctx.channel().isActive()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Snapshot of connected-at timestamps for health reporting. */
    public Map<ServiceType, Long> connectedAtSnapshot() {
        return Map.copyOf(this.connectedAt);
    }

    // ── node factory ─────────────────────────────────────────────────────────

    /**
     * Identifies a connecting service by its source port matching a configured
     * node.
     *
     * FIX: on reconnect, the parent's cache contained the old node with a stale
     * ctx.
     * We now call .ctx(ctx) on cached nodes and remove them from cache after reuse.
     *
     * FALLBACK: if no port match is found (Docker NAT / ephemeral ports), we create
     * a placeholder node — the service will self-identify via
     * SystemConnectionTransaction.
     */
    @Override
    public ServiceNode createNode(LoadBalancerServer<ServiceNode> self,
            ChannelHandlerContext ctx,
            InetSocketAddress address) {
        final int port = address.getPort();
        final ServiceHub hub = (ServiceHub) self;

        // fast path: reconnecting node in parent cache — update ctx to new connection
        if (cache.containsKey(port)) {
            final ServiceNode cached = this.cache.remove(port);
            return cached.ctx(ctx);
        }

        // match port to configured service nodes
        for (final ServiceType type : ServiceType.values()) {
            for (final ServiceNodeConfig nodeConfig : hub.config().getServiceNodes(type)) {
                if (port != nodeConfig.getPort())
                    continue;
                // reuse existing node (if type already connected) or allocate new one
                final ServiceNode existing = this.byType.get(type);
                return (existing != null ? existing : new ServiceNode(hub, nodeConfig)).ctx(ctx);
            }
        }

        // fallback: port not in config — common with Docker/NAT where the service's
        // outbound port is ephemeral. Accept as unknown; SystemConnectionTransaction
        // will identify it.
        logger.warn("[ServicesPool] No configured node for incoming port "
                + port + " — accepted as unknown, awaiting self-identification");
        return new ServiceNode(hub, new ServiceNodeConfig(null, address.getHostString(), port, -1, -1)).ctx(ctx);
    }
}
