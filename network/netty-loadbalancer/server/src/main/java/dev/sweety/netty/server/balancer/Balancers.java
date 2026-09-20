package dev.sweety.netty.server.balancer;

import dev.sweety.util.logger.LogHelper;
import dev.sweety.netty.server.backend.BackendNode;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.io.Serializable;
import java.lang.reflect.Array;
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
    LEAST_CONNECTIONS(new Balancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            T best = activeNodes[0];
            int minInFlight = best.inFlight().get();
            for (int i = 1; i < activeNodes.length; i++) {
                int current = activeNodes[i].inFlight().get();
                if (current < minInFlight) {
                    minInFlight = current;
                    best = activeNodes[i];
                }
            }
            return best;
        }
    }),

    CONSISTENT_HASH(new Balancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            long key = 0L;
            if (packet instanceof dev.sweety.netty.packet.internal.InternalPacket internal && internal.hasRequest()) {
                dev.sweety.netty.packet.internal.RoutingContext routingCtx = internal.getRequest().context();
                if (routingCtx instanceof dev.sweety.netty.packet.internal.RoutingContext.ShardRoutingContext shardCtx) {
                    key = shardCtx.shardKey();
                } else if (routingCtx instanceof dev.sweety.netty.packet.internal.RoutingContext.SessionRoutingContext sessionCtx && sessionCtx.clientSessionId() != null) {
                    key = sessionCtx.clientSessionId().getMostSignificantBits();
                } else if (routingCtx instanceof dev.sweety.netty.packet.internal.RoutingContext.CompositeRoutingContext compCtx) {
                    key = compCtx.shardKey() != 0 ? compCtx.shardKey() : (compCtx.clientSessionId() != null ? compCtx.clientSessionId().getMostSignificantBits() : 0L);
                }
            }
            if (key == 0L && ctx != null) {
                key = ctx.channel().id().asLongText().hashCode();
            }
            int index = Math.floorMod(Long.hashCode(key), activeNodes.length);
            return activeNodes[index];
        }
    }),

    VIRTUAL_SHARD(new Balancer() {
        private final dev.sweety.netty.routing.VirtualShardRouterAdapter<Packet, BackendNode> router =
                new dev.sweety.netty.routing.VirtualShardRouterAdapter<>(p -> {
                    if (p instanceof dev.sweety.netty.packet.internal.InternalPacket internal && internal.hasRequest()) {
                        dev.sweety.netty.packet.internal.RoutingContext routingCtx = internal.getRequest().context();
                        if (routingCtx instanceof dev.sweety.netty.packet.internal.RoutingContext.ShardRoutingContext shardCtx) {
                            return shardCtx.shardKey();
                        } else if (routingCtx instanceof dev.sweety.netty.packet.internal.RoutingContext.CompositeRoutingContext compCtx) {
                            return compCtx.shardKey();
                        }
                    }
                    return 0L;
                });

        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
            java.util.List<T> list = java.util.Arrays.asList(activeNodes);
            //noinspection unchecked
            return (T) router.route(packet, (java.util.List<BackendNode>) (java.util.List<?>) list);
        }
    }),

    LOWEST_PACKET_CPU_TIME(new CounterBalancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
            final int id = packet != null ? packet.name().hashCode() : 0;
            return fromParam(activeNodes, (n) -> n.avgPacketTime(id) / n.maxObservedPacketTime());
        }
    }),

    OPTIMIZED_ADAPTIVE(fromParam(BackendNode::totalScore)),

    ROUND_OPTIMIZED_PACKET_ADAPTIVE(new CounterBalancer() {
        @Override
        public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx, AtomicInteger counter) {
            int start = counter.getAndUpdate(v -> (v + 1) % activeNodes.length);
            int limit = Math.min(3, activeNodes.length);

            final int packetId = packet != null ? packet.name().hashCode() : 0;
            final double factor = 0.35f;

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
        T[] result = (T[]) Array.newInstance(array.getClass().getComponentType(), limit);
        for (int i = 0; i < limit; i++) {
            result[i] = array[(start + i) % array.length];
        }
        return result;
    }

    private static Balancer fromParam(Function<BackendNode, Double> score) {
        return new Balancer() {
            @Override
            public <T extends BackendNode> T nextNode(T[] activeNodes, LogHelper logger, Packet packet, ChannelHandlerContext ctx) {
                //noinspection unchecked
                return (T) fromParam(activeNodes, score);
            }
        };
    }

    private static <T extends BackendNode> T fromParam(T[] activeNodes, Function<T, Double> score) {
        double avgScore = Arrays.stream(activeNodes)
                .mapToDouble(score::apply)
                .average()
                .orElse(1.0);
        return Arrays.stream(activeNodes)
                .filter(n -> score.apply(n) <= avgScore * 1.1)
                .min(comparingdouble(score))
                .orElse(activeNodes[0]);
    }

    public static <T> Comparator<T> comparingdouble(Function<? super T, Double> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable) (c1, c2) -> Double.compare(keyExtractor.apply(c1), keyExtractor.apply(c2));
    }

}
