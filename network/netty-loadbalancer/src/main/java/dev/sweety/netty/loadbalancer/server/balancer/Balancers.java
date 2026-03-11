package dev.sweety.netty.loadbalancer.server.balancer;

import dev.sweety.logger.LogHelper;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public enum Balancers {

    ROUND_ROBIN(new CounterBalancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
            return activeNodes[counter.getAndUpdate(v -> v + 1) % activeNodes.length];
        }
    }),

    RANDOM(new Balancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            return activeNodes[ThreadLocalRandom.current().nextInt(activeNodes.length)];
        }
    }),

    LOWEST_USAGE(fromParam(BackendNode::usageScore)),
    LOWEST_LATENCY(fromParam(BackendNode::latencyScore)),
    LOWEST_BANDWIDTH(fromParam(BackendNode::bandwidthScore)),

    LOWEST_PACKET_CPU_TIME(new CounterBalancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
            final int id = packet.id();
            return fromParam(activeNodes, (n) -> n.avgPacketTime(id) / n.maxObservedPacketTime());
        }
    }),

    OPTIMIZED_ADAPTIVE(fromParam(BackendNode::totalScore)),

    ROUND_OPTIMIZED_PACKET_ADAPTIVE(new CounterBalancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
            int start = counter.getAndUpdate(v -> (v + 1) % activeNodes.length);
            int limit = Math.min(3, activeNodes.length);

            final int packetId = packet.id();
            final float factor = 0.35f;

            return  fromParam(slice(activeNodes, start, limit), (n) ->
                    (factor * n.totalScore()) + ((1 - factor) * n.avgPacketTime(packetId) / n.maxObservedPacketTime()));
        }
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

    @SuppressWarnings("unchecked")
    private static <T> T[] slice(T[] array, int start, int limit) {
        T[] result = (T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), limit);
        for (int i = 0; i < limit; i++) {
            result[i] = array[(start + i) % array.length];
        }
        return result;
    }

    private static Balancer fromParam(Function<BackendNode, Float> score) {
        return new Balancer() {
            @Override
            public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
                //noinspection unchecked
                return (T) fromParam(activeNodes, score);
            }
        };
    }

    private static <T extends BackendNode> T fromParam(T[] activeNodes, Function<T, Float> score) {
        float avgScore = (float) Arrays.stream(activeNodes)
                .mapToDouble(score::apply)
                .average()
                .orElse(1.0);
        return Arrays.stream(activeNodes)
                .filter(n -> score.apply(n) <= avgScore * 1.1f)
                .min(comparingFloat(score))
                .orElse(activeNodes[0]);
    }

    public static <T> Comparator<T> comparingFloat(Function<? super T, Float> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable) (c1, c2) -> Float.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }

}
