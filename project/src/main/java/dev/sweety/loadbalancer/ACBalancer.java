package dev.sweety.loadbalancer;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.loadbalancer.backend.pool.balancer.BalancerSystem;
import dev.sweety.network.cloud.loadbalancer.backend.pool.balancer.Balancers;
import dev.sweety.network.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ACBalancer implements BalancerSystem {

    private final BalancerSystem baseSystem;

    private final Map<UUID, Integer> playerNodeMap = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> nodeLoadMap = new ConcurrentHashMap<>();

    public ACBalancer(Balancers balancer) {
        this.baseSystem = balancer.get();
    }

    @Override
    public BackendNode nextBackend(List<BackendNode> pool, EcstacyLogger logger, Packet packet, ChannelHandlerContext ctx) {

        if (!(packet instanceof PlayerPacket playerPacket)) return baseSystem.nextBackend(pool, logger, packet, ctx);

        final UUID id = playerPacket.getUuid();

        if (playerNodeMap.containsKey(id)) {
            int index = playerNodeMap.get(id);
            if (index >= 0 && index < pool.size()) {
                BackendNode node = pool.get(index);
                if (node.isActive()) {
                    return node;
                }
            }
        }

        BackendNode node = getMinLoaded(pool);
        playerNodeMap.put(id, pool.indexOf(node));
        return node;
    }

    private BackendNode getMinLoaded(List<BackendNode> pool) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < pool.size(); i++) {
            BackendNode node = pool.get(i);
            if (!node.isActive()) continue;
            int load = nodeLoadMap.getOrDefault(i, 0);
            if (load < min) {
                min = load;
            }
        }
        //fallback
        return RandomUtils.randomElement(pool);
    }
}
