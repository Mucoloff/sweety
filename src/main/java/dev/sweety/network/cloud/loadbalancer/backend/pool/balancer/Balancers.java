package dev.sweety.network.cloud.loadbalancer.backend.pool.balancer;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@AllArgsConstructor
public enum Balancers {

    ROUND_ROBIN(new BalancerSystem() {
        private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

        @Override
        public BackendNode nextBackend(List<BackendNode> pool, EcstacyLogger logger, PacketIn packet, ChannelHandlerContext ctx) {
            return IntStream.range(0, pool.size())
                    .map(i -> roundRobinCounter.getAndIncrement() % pool.size())
                    .mapToObj(pool::get)
                    .filter(BackendNode::isActive)
                    .findFirst()
                    .orElse(null);
        }

    }),
    IP_HASH((pool, logger, packet, ctx) -> {
        if (ctx == null || ctx.channel() == null || ctx.channel().remoteAddress() == null)
            return null;
        int hash = ctx.channel().remoteAddress().hashCode();
        return IntStream.range(0, pool.size())
                .map(i -> Math.abs((hash + i)) % pool.size())
                .mapToObj(pool::get)
                .filter(BackendNode::isActive)
                .findFirst()
                .orElse(null);
    }),
    LOWEST_LOAD((pool, logger, packet, ctx) -> pool.stream()
            .filter(BackendNode::isActive)
            .min(Comparator.comparingDouble(BackendNode::getCombinedLoad))
            .orElse(null)),
    LOWEST_LATENCY((pool, logger, packet, ctx) -> pool.stream()
            .filter(BackendNode::isActive)
            .min(Comparator.comparingDouble(BackendNode::getAverageLatency))
            .orElse(null)),
    RANDOM((pool, logger, packet, ctx) -> IntStream.range(0, pool.size())
            .mapToObj(i -> RandomUtils.randomElement(pool))
            .filter(BackendNode::isActive)
            .findFirst()
            .orElse(null)),
    OPTIMIZED_ADAPTIVE(new BalancerSystem() {
        private final AtomicInteger rrCounter = new AtomicInteger(0);

        @Override
        public BackendNode nextBackend(List<BackendNode> pool, EcstacyLogger logger, PacketIn packet, ChannelHandlerContext ctx) {
            if (pool.isEmpty()) return null;

            // Calcola metriche globali
            double avgLoad = pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getCombinedLoad).average().orElse(0.0);
            double avgLatency = pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getAverageLatency).average().orElse(0.0);

            double maxLoad = pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getCombinedLoad).max().orElse(1.0);
            double maxLatency = pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getAverageLatency).max().orElse(1.0);

            // Pesi adattivi (alpha: carico, beta: latenza)
            double loadVariance = Math.max(0.05, Math.abs(avgLoad - maxLoad));
            double latencyVariance = Math.max(0.05, Math.abs(avgLatency - maxLatency));

            double alpha = latencyVariance / (loadVariance + latencyVariance);
            double beta = loadVariance / (loadVariance + latencyVariance);

            int size = pool.size();
            int startIndex = rrCounter.getAndIncrement() % size;

            // Sottoinsieme dei candidati (es. 3)


            List<BackendNode> candidates =

            size > 10 ?
                     IntStream.range(0, 3)
                    .mapToObj(i -> pool.get((startIndex + i) % size))
                    .filter(BackendNode::isActive)
                    .toList() : pool.stream()
                    .filter(BackendNode::isActive)
                    .toList();

            if (candidates.isEmpty()) return null;

            // Calcola score dinamico e sceglie il migliore
            return candidates.stream()
                    .min(Comparator.comparingDouble(node -> {
                        double normalizedLoad = node.getCombinedLoad() / Math.max(1e-9, maxLoad);
                        double normalizedLatency = node.getAverageLatency() / Math.max(1e-9, maxLatency);
                        return alpha * normalizedLoad + beta * normalizedLatency;
                    }))
                    .orElse(null);
        }
    });

    private final BalancerSystem balancerSystem;

    public BalancerSystem get() {
        return this.balancerSystem;
    }

}
