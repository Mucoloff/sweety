package dev.sweety.netty.loadbalancer.refact.lb.pool;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.refact.lb.backend.Node;
import dev.sweety.netty.loadbalancer.refact.lb.balancer.Balancer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

public record NodePool(SimpleLogger logger, List<Node> pool,
                       Balancer balancer) implements INodePool {

    public NodePool(Balancer balancerSystem, Node... nodes) {
        this(new SimpleLogger(NodePool.class), List.of(nodes), balancerSystem);
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
        this.pool.forEach(Node::start);
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    @Override
    public Node next(Packet packet, ChannelHandlerContext ctx) {
        if (this.pool.isEmpty()) return null;
        final List<Node> activeNodes = this.pool.stream().filter(Node::isActive).toList();
        if (activeNodes.isEmpty()) return null;
        logger.info("Active nodes:", activeNodes.stream().map(n -> n.getPort()+"").toList());
        Node node = balancer.nextNode(activeNodes, logger, packet, ctx);
        logger.info("Selected node: " + node.getPort());
        return node;
    }
}
