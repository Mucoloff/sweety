package dev.sweety.network.cloud.loadbalancer.backend.pool;

import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface IBackendPool {
    void initialize();

    BackendNode nextBackend(PacketIn packet, ChannelHandlerContext ctx);

    List<BackendNode> pool();
}
