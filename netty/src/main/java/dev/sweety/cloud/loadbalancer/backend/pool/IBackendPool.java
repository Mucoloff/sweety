package dev.sweety.cloud.loadbalancer.backend.pool;

import dev.sweety.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface IBackendPool {
    void initialize();

    BackendNode nextBackend(Packet packet, ChannelHandlerContext ctx);

    List<BackendNode> pool();
}
