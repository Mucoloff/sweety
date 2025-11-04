package dev.sweety.network.cloud.loadbalancer.backend.pool;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.loadbalancer.backend.pool.balancer.BalancerSystem;
import dev.sweety.network.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public record BackendPool(EcstacyLogger logger, List<BackendNode> pool,
                          BalancerSystem balancerSystem) implements IBackendPool {

    /**
     * Inizializza il pool, connettendo ogni backend.
     */


    @Override
    public void initialize() {
        if (pool.isEmpty()) {
            logger.warn("Nessun backend configurato.");
            return;
        }
        pool.forEach(BackendNode::connect);
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    @Override
    public BackendNode nextBackend(Packet packet, ChannelHandlerContext ctx) {
        if (pool.isEmpty()) return null;

        return balancerSystem.nextBackend(pool, logger, packet, ctx);
    }
}
