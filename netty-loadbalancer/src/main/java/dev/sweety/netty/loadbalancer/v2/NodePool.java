package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.loadbalancer.backend.pool.balancer.BalancerSystem;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public record NodePool(SimpleLogger logger, List<Node> pool,
                          BalancerSystem balancerSystem) {

    public NodePool(BalancerSystem balancerSystem, Node... nodes) {
        this(new SimpleLogger(NodePool.class), List.of(nodes), balancerSystem);
    }

    /**
     * Inizializza il pool, connettendo ogni backend.
     */

    public void initialize() {
        if (this.pool.isEmpty()) {
            this.logger.warn("Nessun backend configurato.");
            return;
        }
        this.pool.forEach(Node::start);
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    public Node nextBackend(Packet packet, ChannelHandlerContext ctx) {
        if (pool.isEmpty()) return null;
        final List<Node> activeNodes = this.pool.stream().filter(Node::isActive).toList();
        if (activeNodes.isEmpty()) return null;
        return RandomUtils.randomElement(activeNodes);
        //return balancerSystem.nextBackend(pool, logger, packet, ctx);
    }
}
