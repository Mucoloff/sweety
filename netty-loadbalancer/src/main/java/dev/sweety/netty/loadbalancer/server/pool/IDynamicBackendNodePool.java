package dev.sweety.netty.loadbalancer.server.pool;


import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.function.Predicate;

public interface IDynamicBackendNodePool<T extends BackendNode> extends IBackendNodePool<T> {
    void add(ChannelHandlerContext ctx, T node);

    T createNode(LoadBalancerServer<T> self, ChannelHandlerContext ctx, InetSocketAddress address);

    default T createAndAdd(LoadBalancerServer<T> self, ChannelHandlerContext ctx, InetSocketAddress address) {
        final T node = createNode(self, ctx, address);
        add(ctx, node);
        return node;
    }

    T remove(ChannelHandlerContext ctx);

    ChannelHandlerContext remove(T node);

    T get(ChannelHandlerContext ctx);

    <N extends BackendNode> ChannelHandlerContext context(N node);

    @Override
    Collection<T> pool();

    Predicate<T> predicate(Packet packet, ChannelHandlerContext ctx);

    @Override
    T next(Packet packet, ChannelHandlerContext ctx);
}
