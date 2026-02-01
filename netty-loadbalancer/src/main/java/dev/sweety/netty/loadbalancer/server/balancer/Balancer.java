package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.core.logger.LogHelper;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.atomic.AtomicInteger;

@FunctionalInterface
public interface Balancer extends CounterBalancer {

    BackendNode nextNode(BackendNode[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx);

    @Override
    default BackendNode nextNode(BackendNode[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
        return nextNode(activeNodes, logger, packet, ctx);
    }
}
