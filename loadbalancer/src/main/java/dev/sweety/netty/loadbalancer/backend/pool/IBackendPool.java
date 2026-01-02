package dev.sweety.netty.loadbalancer.backend.pool;

import dev.sweety.netty.loadbalancer.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface IBackendPool {
    void initialize();

    BackendNode nextBackend(Packet packet, ChannelHandlerContext ctx);

    List<BackendNode> pool();
}
