package dev.sweety.saas.hub.backend.pool;

import dev.sweety.math.list.ConcurrentHashSet;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServicesPool extends DynamicBackendNodePool<ServiceNode> {

    public static class ServiceCluster {
        public final ServiceType type;
        public final Set<ServiceNode> nodes = new ConcurrentHashSet<>();
        public final Map<ServiceNode, Long> connectedAt = new ConcurrentHashMap<>();

        public ServiceCluster(ServiceType type) {
            this.type = type;
        }

        public void add(ServiceNode node) {
            nodes.add(node);
            connectedAt.put(node, System.currentTimeMillis());
        }

        public void remove(ServiceNode node) {
            nodes.remove(node);
            connectedAt.remove(node);
        }

        public boolean isConnected() {
            if (nodes.isEmpty()) return false;
            for (ServiceNode node : nodes) {
                if (node.context() != null && node.context().channel().isActive()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }
    }

    private final Map<ServiceType, ServiceCluster> clusters = new ConcurrentHashMap<>();

    public ServicesPool(CounterBalancer balancer) {
        super(balancer);
    }

    public ServicesPool(Balancers balancer) {
        super(balancer);
    }

    private ServiceCluster getCluster(ServiceType type) {
        return clusters.computeIfAbsent(type, ServiceCluster::new);
    }

    // ── pool lifecycle ─────────────────────────────────────────────────────

    @Override
    public ServiceNode createAndAdd(LoadBalancerServer<ServiceNode> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final ServiceNode node = super.createAndAdd(self, ctx, address);
        if (node.type() != null)
            promoteNode(node.type(), node);
        return node;
    }

    @Override
    public synchronized ServiceNode remove(ChannelHandlerContext ctx) {
        ServiceNode removed = super.remove(ctx);
        if (removed != null && removed.type() != null) {
            ServiceCluster cluster = clusters.get(removed.type());
            if (cluster != null) {
                cluster.remove(removed);
                if (cluster.isEmpty()) clusters.remove(removed.type());
            }
        }
        return removed;
    }

    @Override
    public synchronized ChannelHandlerContext remove(ServiceNode node) {
        if (node.type() != null) {
            ServiceCluster cluster = clusters.get(node.type());
            if (cluster != null) {
                cluster.remove(node);
                if (cluster.isEmpty()) clusters.remove(node.type());
            }
        }
        return super.remove(node);
    }

    // ── lookup ──────────────────────────────────────────────────────────────

    /**
     * Promote a placeholder node (null type) to a known type after self-identification.
     */
    public synchronized void promoteNode(ServiceType type, ServiceNode node) {
        getCluster(type).add(node);
    }

    /**
     * True only if the service has at least one active, writable channel.
     */
    public boolean isConnected(ServiceType type) {
        ServiceCluster cluster = clusters.get(type);
        return cluster != null && cluster.isConnected();
    }

    /**
     * Returns all ServiceTypes that currently have an active connection.
     */
    public Set<ServiceType> connectedTypes() {
        Set<ServiceType> result = new HashSet<>();
        for (Map.Entry<ServiceType, ServiceCluster> entry : clusters.entrySet()) {
            if (entry.getValue().isConnected()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Returns the internal active clusters view.
     */
    public Map<ServiceType, ServiceCluster> viewClusters() {
        return Collections.unmodifiableMap(clusters);
    }

    // ── node factory ─────────────────────────────────────────────────────────

    @Override
    public ServiceNode createNode(LoadBalancerServer<ServiceNode> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final int port = address.getPort();
        final ServiceHub hub = (ServiceHub) self;

        if (cache.containsKey(port)) {
            final ServiceNode cached = this.cache.remove(port);
            return cached.ctx(ctx);
        }

        for (final ServiceType type : ServiceType.values()) {
            for (final ServiceNodeConfig nodeConfig : hub.config().getServiceNodes(type)) {
                if (port != nodeConfig.getPort()) continue;
                return new ServiceNode(hub, nodeConfig).ctx(ctx);
            }
        }

        logger.warn("[ServicesPool] No configured node for incoming port " + port + " — accepted as unknown, awaiting self-identification");
        return new ServiceNode(hub, new ServiceNodeConfig(null, address.getHostString(), port, -1, -1)).ctx(ctx);
    }

}
