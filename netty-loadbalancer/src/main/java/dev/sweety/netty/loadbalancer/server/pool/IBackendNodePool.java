package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.function.Consumer;

public interface IBackendNodePool {

    void initialize();

    BackendNode next(Packet packet, ChannelHandlerContext ctx);

    BackendNode[] pool();

    default void foreachNode(Consumer<BackendNode> consumer){
        for (BackendNode node : pool()) consumer.accept(node);
    }
}
