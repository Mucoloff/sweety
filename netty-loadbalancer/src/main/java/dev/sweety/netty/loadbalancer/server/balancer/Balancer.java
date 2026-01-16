package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.core.logger.LogHelper;
import dev.sweety.netty.loadbalancer.server.backend.Node;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

@FunctionalInterface
public interface Balancer {

    Node nextNode(List<Node> activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx);

}
