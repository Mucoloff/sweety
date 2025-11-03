package dev.sweety.network.cloud.loadbalancer.backend.pool.balancer;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public interface BalancerSystem {

    BackendNode nextBackend(List<BackendNode> pool, EcstacyLogger logger, PacketIn packet, ChannelHandlerContext ctx);

}
