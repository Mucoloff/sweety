package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.balancer.Balancer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public record BackendNodePool(SimpleLogger logger, List<BackendNode> pool,
                              Balancer balancer) implements IBackendNodePool {

    public BackendNodePool(Balancer balancerSystem, BackendNode... nodes) {
        this(new SimpleLogger(BackendNodePool.class), List.of(nodes), balancerSystem);
    }

    /**
     * Inizializza il pool, connettendo ogni backend.
     */

    @Override
    public void initialize() {
        if (this.pool.isEmpty()) {
            this.logger.warn("Nessun backend configurato.");
            return;
        }
        this.pool.forEach(BackendNode::start);
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    @Override
    public BackendNode next(Packet packet, ChannelHandlerContext ctx) {
        if (this.pool.isEmpty()) return null;
        final List<BackendNode> activeNodes = this.pool.stream().filter(BackendNode::isActive).filter(BackendNode::canAcceptPacket).toList();
        if (activeNodes.isEmpty()) return null;
        return balancer.nextNode(activeNodes, logger, packet, ctx);
    }
}
