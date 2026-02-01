package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.netty.loadbalancer.server.backend.BackendNode;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public enum Balancers {

    ROUND_ROBIN((activeNodes, logger, packet, ctx, counter) -> {
        int index = counter.getAndUpdate(v -> (v + 1) % activeNodes.length);
        return activeNodes[index];
    }),

    RANDOM((activeNodes, logger, packet, ctx) ->
        activeNodes[ThreadLocalRandom.current().nextInt(activeNodes.length)]),

    LOWEST_USAGE(fromParam(BackendNode::getUsageScore)),
    LOWEST_LATENCY(fromParam(BackendNode::getLatencyScore)),
    LOWEST_BANDWIDTH(fromParam(BackendNode::getBandwidthScore)),

    LOWEST_PACKET_CPU_TIME((activeNodes, logger, packet, ctx, counter) -> {
        final int id = packet.id();
        final Function<BackendNode, Float> nodeFloatFunction = (n) -> n.getAvgPacketTime(id) / n.getMaxObservedPacketTime();
        return fromParam(activeNodes, nodeFloatFunction);
    }),

    OPTIMIZED_ADAPTIVE(fromParam(BackendNode::getTotalScore)),

    ROUND_OPTIMIZED_PACKET_ADAPTIVE((activeNodes, logger, packet, ctx, counter) -> {
        int start = counter.getAndUpdate(v -> (v + 1) % activeNodes.length);
        int limit = Math.min(3, activeNodes.length);

        BackendNode[] candidates = new BackendNode[limit];
        for (int i = 0; i < limit; i++) {
            candidates[i] = activeNodes[(start + i) % activeNodes.length];
        }

        final int packetId = packet.id();
        final float factor = 0.35f;
        final Function<BackendNode, Float> calcScore = (n) ->
            (factor * n.getTotalScore()) + ((1 - factor) * n.getAvgPacketTime(packetId) / n.getMaxObservedPacketTime());

        return fromParam(candidates, calcScore);
    });

    private final CounterBalancer balancer;

    Balancers(CounterBalancer balancer) {
        this.balancer = balancer;
    }

    Balancers(Balancer balancer) {
        this.balancer = balancer;
    }

    public CounterBalancer get() {
        return balancer;
    }

    private static BackendNode fromParam(BackendNode[] activeNodes, Function<BackendNode, Float> score) {
        float avgScore = (float) Arrays.stream(activeNodes)
                .mapToDouble(score::apply)
                .average()
                .orElse(1.0);
        return Arrays.stream(activeNodes)
                .filter(n -> score.apply(n) <= avgScore * 1.1f)
                .min(comparingFloat(score))
                .orElse(activeNodes[0]);
    }

    private static Balancer fromParam(Function<BackendNode, Float> score) {
        return (activeNodes, logger, packet, ctx) -> fromParam(activeNodes, score);
    }

    public static <T> Comparator<T> comparingFloat(Function<? super T, Float> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable) (c1, c2) -> Float.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }
}
