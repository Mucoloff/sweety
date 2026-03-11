package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.logger.LogHelper;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;

@FunctionalInterface
public interface Balancer extends CounterBalancer {

    <T extends BackendNode> T nextNode(@NotNull T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx);

    @Override
    default  <T extends BackendNode> T nextNode(@NotNull T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger roundRobinCounter) {
        return nextNode(activeNodes, logger, packet, ctx);
    }
}
