package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.netty.loadbalancer.server.backend.Node;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface INodePool {

    void initialize();

    Node next(Packet packet, ChannelHandlerContext ctx);

    List<Node> pool();
}
