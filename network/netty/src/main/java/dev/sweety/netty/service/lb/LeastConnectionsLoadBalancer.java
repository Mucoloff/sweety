package dev.sweety.netty.service.lb;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Least-Connections and Power-of-Two-Choices (P2C) client-side load balancer.
 */
public final class LeastConnectionsLoadBalancer implements LoadBalancer {

    public static final AttributeKey<AtomicInteger> ACTIVE_REQUESTS =
            AttributeKey.valueOf("aurora.lb.active_requests");

    private static final LeastConnectionsLoadBalancer INSTANCE = new LeastConnectionsLoadBalancer();

    private LeastConnectionsLoadBalancer() {}

    public static LeastConnectionsLoadBalancer getInstance() {
        return INSTANCE;
    }

    @Override
    public Channel select(List<Channel> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        // Power of Two Choices (P2C) to avoid stampede effects
        if (candidates.size() >= 4) {
            int i1 = (int) (Math.random() * candidates.size());
            int i2 = (int) (Math.random() * candidates.size());
            while (i1 == i2) {
                i2 = (int) (Math.random() * candidates.size());
            }

            Channel c1 = candidates.get(i1);
            Channel c2 = candidates.get(i2);

            return getActiveCount(c1) <= getActiveCount(c2) ? c1 : c2;
        }

        // Standard Least-Connections scan for smaller pools
        Channel best = null;
        int minRequests = Integer.MAX_VALUE;

        for (Channel ch : candidates) {
            if (!ch.isActive()) continue;
            int count = getActiveCount(ch);
            if (count < minRequests) {
                minRequests = count;
                best = ch;
            }
        }

        return best != null ? best : candidates.get(0);
    }

    public static int getActiveCount(Channel ch) {
        if (ch == null) return Integer.MAX_VALUE;
        AtomicInteger counter = ch.attr(ACTIVE_REQUESTS).get();
        return counter != null ? counter.get() : 0;
    }

    public static void increment(Channel ch) {
        if (ch != null) {
            ch.attr(ACTIVE_REQUESTS).setIfAbsent(new AtomicInteger(0));
            ch.attr(ACTIVE_REQUESTS).get().incrementAndGet();
        }
    }

    public static void decrement(Channel ch) {
        if (ch != null) {
            AtomicInteger counter = ch.attr(ACTIVE_REQUESTS).get();
            if (counter != null) {
                counter.decrementAndGet();
            }
        }
    }
}
