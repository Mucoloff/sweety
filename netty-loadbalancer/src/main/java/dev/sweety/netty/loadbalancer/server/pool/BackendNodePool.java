package dev.sweety.netty.loadbalancer.server.pool;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.balancer.CounterBalancer;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackendNodePool implements IBackendNodePool {

    private final SimpleLogger logger;
    private final BackendNode[] pool;
    private final CounterBalancer balancer;
    private final AtomicInteger counter = new AtomicInteger();

    public BackendNodePool(CounterBalancer balancer, BackendNode... nodes) {
        this.logger = new SimpleLogger(BackendNodePool.class);
        this.pool = nodes;
        this.balancer = balancer;
    }

    /**
     * Inizializza il pool, connettendo ogni backend.
     */
    @Override
    public void initialize() {
        if (this.pool.length == 0) {
            this.logger.warn("Nessun backend configurato.");
            return;
        }
        for (BackendNode node : pool) {
            node.start();
        }
    }

    @Override
    public BackendNode[] pool() {
        return pool;
    }

    /**
     * Seleziona il prossimo backend disponibile.
     *
     * @return Il BackendNode selezionato, o null se nessuno è disponibile.
     */
    @Override
    public BackendNode next(Packet packet, ChannelHandlerContext ctx) {
        if (this.pool.length == 0) return null;

        final BackendNode[] activeNodes = Arrays.stream(pool)
                .filter(BackendNode::isActive)
                .filter(BackendNode::canAcceptPacket)
                .toArray(BackendNode[]::new);

        if (activeNodes.length == 0) return null;

        return balancer.nextNode(activeNodes, logger, packet, ctx, counter);
    }

}
