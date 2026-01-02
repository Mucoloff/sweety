package dev.sweety.cloud.loadbalancer.backend.pool.balancer;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.cloud.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

@AllArgsConstructor
public enum Balancers {

    ROUND_ROBIN(new BalancerSystem() {
        private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

        @Override
        public BackendNode nextBackend(List<BackendNode> pool, SimpleLogger logger, Packet packet, ChannelHandlerContext ctx) {
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
            .min(comparingFloat(BackendNode::getCombinedLoad))
            .orElse(null)),
    LOWEST_LATENCY((pool, logger, packet, ctx) -> pool.stream()
            .filter(BackendNode::isActive)
            .min(comparingFloat(BackendNode::getAverageLatency))
            .orElse(null)),
    RANDOM((pool, logger, packet, ctx) -> IntStream.range(0, pool.size())
            .mapToObj(i -> RandomUtils.randomElement(pool))
            .filter(BackendNode::isActive)
            .findFirst()
            .orElse(null)),
    OPTIMIZED_ADAPTIVE(new BalancerSystem() {
        private final AtomicInteger rrCounter = new AtomicInteger(0);

        @Override
        public BackendNode nextBackend(List<BackendNode> pool, SimpleLogger logger, Packet packet, ChannelHandlerContext ctx) {
            if (pool.isEmpty()) return null;

            // Calcola metriche globali
            float avgLoad = (float) pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getCombinedLoad).average().orElse(0.0);
            float avgLatency = (float) pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getAverageLatency).average().orElse(0.0);

            float maxLoad = (float) pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getCombinedLoad).max().orElse(1.0);
            float maxLatency = (float) pool.stream().filter(BackendNode::isActive)
                    .mapToDouble(BackendNode::getAverageLatency).max().orElse(1.0);

            // Pesi adattivi (alpha: carico, beta: latenza)
            float loadVariance = (float) Math.max(0.05, Math.abs(avgLoad - maxLoad));
            float latencyVariance = (float) Math.max(0.05, Math.abs(avgLatency - maxLatency));

            float alpha = latencyVariance / (loadVariance + latencyVariance);
            float beta = loadVariance / (loadVariance + latencyVariance);

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
                    .min(comparingFloat(node -> {
                        float normalizedLoad = (float) (node.getCombinedLoad() / Math.max(1e-9, maxLoad));
                        float normalizedLatency = (float) (node.getAverageLatency() / Math.max(1e-9, maxLatency));
                        return alpha * normalizedLoad + beta * normalizedLatency;
                    }))
                    .orElse(null);
        }
    });

    public static<T> Comparator<T> comparingFloat(Function<? super T,Float> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
                (c1, c2) -> Float.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }

    private final BalancerSystem balancerSystem;

    public BalancerSystem get() {
        return this.balancerSystem;
    }

}
