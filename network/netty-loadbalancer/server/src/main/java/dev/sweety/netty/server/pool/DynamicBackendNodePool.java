package dev.sweety.netty.server.pool;

import dev.sweety.netty.server.LoadBalancerServer;
import dev.sweety.netty.server.backend.BackendNode;
import dev.sweety.netty.server.balancer.Balancers;
import dev.sweety.netty.server.balancer.CounterBalancer;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.netty.packet.internal.InternalPacket;
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
    protected volatile BackendNode[] active_nodes_cache = new BackendNode[0];

    public DynamicBackendNodePool(CounterBalancer balancer) {
        this.balancer = balancer;
    }

    public DynamicBackendNodePool(Balancers balancer) {
        this.balancer = balancer.get();
    }

    @Override
    public synchronized void add(ChannelHandlerContext ctx, T node) {
        final ChannelHandlerContext previousCtx = this.reverseNodes.put(node, ctx);
        if (previousCtx != null && previousCtx != ctx) {
            this.nodes.remove(previousCtx);
        }

        final T replaced = this.nodes.put(ctx, node);
        if (replaced != null && replaced != node) {
            this.reverseNodes.remove(replaced);
        }
        this.rebuild_active_cache();
    }

    @Override
    public T createNode(LoadBalancerServer<T> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final int port = address.getPort();
        if (cache.containsKey(port)) {
            final T cached = this.cache.remove(port);
            return cached.ctx(ctx);
        }
        // Type is learned from SystemConnectionTransaction senderId.
        //noinspection unchecked
        return (T) new BackendNode(self, port, -1);
    }

    @Override
    public synchronized T createAndAdd(LoadBalancerServer<T> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final T existingNode = this.nodes.remove(ctx);
        if (existingNode != null) {
            this.reverseNodes.remove(existingNode);
            this.cache.put(existingNode.port(), existingNode);
        }
        final T node = createNode(self, ctx, address);
        add(ctx, node);
        return node;
    }

    @Override
    public synchronized T remove(final ChannelHandlerContext ctx) {
        final T removed = nodes.remove(ctx);
        if (removed != null) {
            this.reverseNodes.remove(removed);
            this.cache.put(removed.port(), removed);
            this.rebuild_active_cache();
        }
        return removed;
    }

    @Override
    public synchronized ChannelHandlerContext remove(final T node) {
        final ChannelHandlerContext ctx = this.reverseNodes.remove(node);
        if (ctx != null) {
            this.nodes.remove(ctx);
            this.cache.put(node.port(), node);
            this.rebuild_active_cache();
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

    protected void rebuild_active_cache() {
        this.active_nodes_cache = this.nodes.values().toArray(new BackendNode[0]);
    }

    protected boolean match_receiver(final Packet packet, final BackendNode node) {
        if (!(packet instanceof InternalPacket internal) || !internal.hasRequest()) {
            return true;
        }
        final int receiver_id = internal.getRequest().receiverId();
        if (receiver_id == -1) {
            return true;
        }
        if (node.typeId() < 0) {
            // Unknown node identity until first internal packet announces sender type.
            return true;
        }
        return node.typeId() == receiver_id;
    }

    @Override
    public Predicate<T> predicate(final Packet packet, final ChannelHandlerContext ctx) {
        return node -> this.match_receiver(packet, node);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T next(Packet packet, ChannelHandlerContext ctx) {
        final BackendNode[] active = this.active_nodes_cache;
        if (active.length == 0) {
            return null;
        }

        final int start = Math.floorMod(this.counter.getAndIncrement(), active.length);
        for (int i = 0; i < active.length; i++) {
            final int index = (start + i) % active.length;
            final BackendNode node = active[index];
            if (node == null) {
                continue;
            }
            if (!this.match_receiver(packet, node)) {
                continue;
            }
            if (!node.tryAcceptPacket()) {
                continue;
            }
            return (T) node;
        }
        return null;
    }
}