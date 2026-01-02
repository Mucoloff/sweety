package dev.sweety.cloud.loadbalancer.backend.pool.balancer;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface BalancerSystem {

    BackendNode nextBackend(List<BackendNode> pool, SimpleLogger logger, Packet packet, ChannelHandlerContext ctx);

}
