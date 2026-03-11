package dev.sweety.netty.loadbalancer.server.pool;


import dev.sweety.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.common.packet.internal.InternalPacket;
import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.balancer.Balancers;
import dev.sweety.netty.loadbalancer.server.balancer.CounterBalancer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class DynamicBackendNodePool<T extends BackendNode> implements IDynamicBackendNodePool<T> {

    protected final SimpleLogger logger = new SimpleLogger(DynamicBackendNodePool.class);

    protected final Map<ChannelHandlerContext, T> nodes = new ConcurrentHashMap<>();
    protected final Map<T, ChannelHandlerContext> reverseNodes = new ConcurrentHashMap<>();

    protected final Map<Integer, T> cache = new ConcurrentHashMap<>();

    protected final CounterBalancer balancer;
    protected final AtomicInteger counter = new AtomicInteger();

    public DynamicBackendNodePool(CounterBalancer balancer) {
        this.balancer = balancer;
    }

    public DynamicBackendNodePool(Balancers balancer) {
        this.balancer = balancer.get();
    }

    @Override
    public synchronized void add(ChannelHandlerContext ctx, T node) {
        this.nodes.put(ctx, node);
        this.reverseNodes.put(node, ctx);
    }

    @Override
    public T createNode(LoadBalancerServer<T> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final int port = address.getPort();
        if (cache.containsKey(port)) return this.cache.get(port);

        int type = port >= 5004 ? 1 : 0;
        //noinspection unchecked
        return (T) new BackendNode(self, port, type);
    }

    @Override
    public synchronized T remove(final ChannelHandlerContext ctx) {
        final T removed = nodes.remove(ctx);
        if (removed != null) {
            this.reverseNodes.remove(removed);
            this.cache.put(removed.port(), removed);
        }
        return removed;
    }

    @Override
    public synchronized ChannelHandlerContext remove(final T node) {
        final ChannelHandlerContext ctx = this.reverseNodes.remove(node);
        if (ctx != null) {
            this.nodes.remove(ctx);
            this.cache.put(node.port(), node);
        }
        return ctx;
    }

    @Override
    public T get(ChannelHandlerContext ctx) {
        return nodes.get(ctx);
    }

    @Override
    public <N extends BackendNode> ChannelHandlerContext context(N node) {
        //noinspection unchecked
        return this.reverseNodes.get((T) node);
    }

    @Override
    public Collection<T> pool() {
        return this.nodes.values();
    }

    @Override
    public Predicate<T> predicate(final Packet packet, final ChannelHandlerContext ctx) {
        return node -> {
            if (packet instanceof InternalPacket i && i.hasRequest()) {
                int receiverId = i.getRequest().receiverId();
                return receiverId == -1 || node.typeId() == receiverId;
            }
            return true;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next(Packet packet, ChannelHandlerContext ctx) {
        if (this.nodes.isEmpty()) return null;

        final T[] activeNodes = (T[]) this.nodes.values().stream()
                .filter(BackendNode::canAcceptPacket)
                .filter(predicate(packet, ctx))
                .toArray(BackendNode[]::new);

        if (activeNodes.length == 0) return null;

        return balancer.nextNode(activeNodes, logger, packet, ctx, counter);
    }
}
