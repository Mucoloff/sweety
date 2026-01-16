package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface IBackendNodePool {

    void initialize();

    BackendNode next(Packet packet, ChannelHandlerContext ctx);

    List<BackendNode> pool();
}
