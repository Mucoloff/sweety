package dev.sweety.netty.service.cluster;

import dev.sweety.math.list.ConcurrentHashSet;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.server.backend.BackendNode;
import dev.sweety.netty.server.balancer.Balancers;
import dev.sweety.netty.server.balancer.CounterBalancer;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generic high-performance cluster partition managing a pool of backend nodes
 * dedicated to a specific service identifier or service type, balanced with a configurable balancer.
 *
 * @param <K> service key or type identifier
 * @param <N> node type extending BackendNode
 */
public class ServiceCluster<K, N extends BackendNode> {

    public final K key;
    protected final Class<N> nodeClass;
    public final Set<N> nodes = new ConcurrentHashSet<>();
    public final Map<N, Long> connectedAt = new ConcurrentHashMap<>();
    private final CounterBalancer balancer;
    private final AtomicInteger clusterCounter = new AtomicInteger();

    public ServiceCluster(@NotNull K key, @NotNull Class<N> nodeClass) {
        this(key, nodeClass, Balancers.ROUND_ROBIN.get());
    }

    public ServiceCluster(@NotNull K key, @NotNull Class<N> nodeClass, @Nullable CounterBalancer balancer) {
        this.key = Objects.requireNonNull(key, "cluster key cannot be null");
        this.nodeClass = Objects.requireNonNull(nodeClass, "nodeClass cannot be null");
        this.balancer = balancer != null ? balancer : Balancers.ROUND_ROBIN.get();
    }

    public K key() {
        return key;
    }

    public void add(@NotNull N node) {
        this.nodes.add(node);
        this.connectedAt.put(node, System.currentTimeMillis());
    }

    public void remove(@NotNull N node) {
        this.nodes.remove(node);
        this.connectedAt.remove(node);
    }

    public boolean isConnected() {
        if (this.nodes.isEmpty()) return false;
        for (N node : this.nodes) {
            if (node.context() != null && node.context().channel().isActive()) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public int size() {
        return this.nodes.size();
    }

    public Set<N> nodes() {
        return this.nodes;
    }

    public CounterBalancer balancer() {
        return this.balancer;
    }

    public Long connectedTime(@NotNull N node) {
        return this.connectedAt.get(node);
    }

    @Nullable
    public N nextNode(@Nullable Packet packet, @Nullable ChannelHandlerContext ctx) {
        @SuppressWarnings("unchecked")
        N[] active = this.nodes.stream()
                .filter(n -> n.context() != null && n.context().channel().isActive() && n.tryAcceptPacket())
                .toArray(size -> (N[]) Array.newInstance(nodeClass, size));

        if (active.length == 0) return null;
        if (active.length == 1) return active[0];
        return this.balancer.nextNode(active, null, packet, ctx, this.clusterCounter);
    }
}
