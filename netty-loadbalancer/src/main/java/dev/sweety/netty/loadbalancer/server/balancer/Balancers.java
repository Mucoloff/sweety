package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.core.logger.LogHelper;
import dev.sweety.core.math.RandomUtils;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@AllArgsConstructor
public enum Balancers {

    ROUND_ROBIN(new Balancer() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public BackendNode nextNode(List<BackendNode> activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            int start = (counter.getAndUpdate(v -> (v + 1) % activeNodes.size()));
            return activeNodes.get(start);
        }
    }),

    RANDOM((activeNodes, logger, packet, ctx) -> RandomUtils.randomElement(activeNodes)),

    LOWEST_USAGE(fromParam(BackendNode::getUsageScore)),
    LOWEST_LATENCY(fromParam(BackendNode::getLatencyScore)),
    LOWEST_BANDWIDTH(fromParam(BackendNode::getBandwidthScore)),

    LOWEST_PACKET_CPU_TIME((activeNodes, logger, packet, ctx) -> {
        final int id = packet.id();
        final Function<BackendNode, Float> nodeFloatFunction = (n) -> n.getAvgPacketTime(id) / n.getMaxObservedPacketTime();
        return fromParam(activeNodes, nodeFloatFunction);
    }),

    OPTIMIZED_ADAPTIVE(fromParam(BackendNode::getTotalScore)),

    ROUND_OPTIMIZED_PACKET_ADAPTIVE(new Balancer() {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public BackendNode nextNode(List<BackendNode> activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            int start = (counter.getAndUpdate(v -> (v + 1) % activeNodes.size()));
            List<BackendNode> candidates = activeNodes.stream()
                    .skip(start)
                    .limit(Math.min(3, activeNodes.size()))
                    .toList();

            final int packetId = packet.id();
            final float factor = 0.35f;
            final Function<BackendNode, Float> calcScore = (n) -> (factor * n.getTotalScore()) + ((1 - factor) * n.getAvgPacketTime(packetId) / n.getMaxObservedPacketTime());

            return fromParam(candidates, calcScore);
        }
    });

    private final Balancer balancer;

    public Balancer get() {
        return balancer;
    }

    private static BackendNode fromParam(List<BackendNode> activeNodes, Function<BackendNode, Float> score) {
        float avgScore = (float) activeNodes.stream()
                .mapToDouble(score::apply)
                .average()
                .orElse(1.0);
        return activeNodes.stream()
                .filter(n -> score.apply(n) <= avgScore * 1.1f)
                .min(comparingFloat(score))
                .orElse(activeNodes.getFirst());
    }

    private static Balancer fromParam(Function<BackendNode, Float> score) {
        return (activeNodes, logger, packet, ctx) -> fromParam(activeNodes, score);
    }

    public static <T> Comparator<T> comparingFloat(Function<? super T, Float> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable) (c1, c2) -> Float.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }
}
