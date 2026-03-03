package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Collection;
import java.util.function.Consumer;

public interface IBackendNodePool<T extends BackendNode> {

    T next(Packet packet, ChannelHandlerContext ctx);

    Collection<T> pool();

    default void foreachNode(Consumer<T> consumer) {
        for (T node : pool()) consumer.accept(node);
    }
}
