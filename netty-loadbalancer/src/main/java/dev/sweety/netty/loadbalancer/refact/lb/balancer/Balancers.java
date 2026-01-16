package dev.sweety.netty.loadbalancer.refact.lb.balancer;

import dev.sweety.core.logger.LogHelper;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.loadbalancer.refact.common.metrics.state.NodeState;
import dev.sweety.netty.loadbalancer.refact.lb.backend.Node;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@AllArgsConstructor
public enum Balancers {

    ROUND_ROBIN(new Balancer() {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Node nextNode(List<Node> activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            int start = counter.getAndIncrement();
            return IntStream.range(0, activeNodes.size())
                    .map(i -> (start + i) % activeNodes.size())
                    .mapToObj(activeNodes::get)
                    .findFirst()
                    .orElse(null);
        }
    }),

    RANDOM((pool, logger, packet, ctx) -> RandomUtils.randomElement(pool)),

    LOWEST_USAGE((pool, logger, packet, ctx) -> pool.stream()
            .min(comparingFloat(Node::getUsageScore))
            .orElse(null)),

    LOWEST_LATENCY((pool, logger, packet, ctx) -> pool.stream()
            .min(comparingFloat(Node::getLatencyScore))
            .orElse(null)),

    LOWEST_BANDWIDTH((pool, logger, packet, ctx) -> pool.stream()
            .min(comparingFloat(Node::getBandwidthScore))
            .orElse(null)),

    OPTIMIZED_ADAPTIVE(new Balancer() {
        private final AtomicInteger rrCounter = new AtomicInteger(0);

        @Override
        public Node nextNode(List<Node> activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            // calcoliamo i max per normalizzazione
            float maxUsage = (float) activeNodes.stream().mapToDouble(Node::getUsageScore).max().orElse(1);
            float maxLatency = (float) activeNodes.stream().mapToDouble(Node::getLatencyScore).max().orElse(1);
            float maxBandwidth = (float) activeNodes.stream().mapToDouble(Node::getBandwidthScore).max().orElse(1);
            float maxCurrentBandwidth = (float) activeNodes.stream().mapToDouble(Node::getCurrentBandwidthScore).max().orElse(1);

            int startIndex = rrCounter.getAndIncrement() % activeNodes.size();

            // score combinato pesato
            return activeNodes.stream()
                    .min(comparingFloat(node -> {
                        float usageNorm = node.getUsageScore() / maxUsage;
                        float latencyNorm = node.getLatencyScore() / maxLatency;
                        float bandwidthNorm = node.getBandwidthScore() / maxBandwidth;
                        float currentBandwidthNorm = node.getCurrentBandwidthScore() / maxCurrentBandwidth;

                        // pesi: 40% uso, 30% latenza, 20% bandwidth totale, 10% current bandwidth
                        float score = 0.4f * usageNorm
                                + 0.3f * latencyNorm
                                + 0.2f * bandwidthNorm
                                + 0.1f * currentBandwidthNorm;

                        // penalità se nodo degradato
                        if (node.getState() == NodeState.DEGRADED) score *= 1.5f;

                        return score;
                    }))
                    .orElse(activeNodes.get(startIndex)); // fallback
        }
    });

    private final Balancer balancer;

    public Balancer get() {
        return balancer;
    }

    public static <T> Comparator<T> comparingFloat(java.util.function.Function<? super T, Float> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable) (c1, c2) -> Float.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }
}
