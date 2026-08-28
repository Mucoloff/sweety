package dev.sweety.netty.service.lb;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeastConnectionsLoadBalancerTest {

    @Test
    void testNullOrEmptyCandidates() {
        LeastConnectionsLoadBalancer lb = LeastConnectionsLoadBalancer.getInstance();
        assertNull(lb.select(null));
        assertNull(lb.select(List.of()));
    }

    @Test
    void testSingleCandidate() {
        LeastConnectionsLoadBalancer lb = LeastConnectionsLoadBalancer.getInstance();
        Channel ch = new EmbeddedChannel();
        assertEquals(ch, lb.select(List.of(ch)));
    }

    @Test
    void testLeastConnectionsSelection() {
        LeastConnectionsLoadBalancer lb = LeastConnectionsLoadBalancer.getInstance();

        Channel ch1 = new EmbeddedChannel();
        Channel ch2 = new EmbeddedChannel();
        Channel ch3 = new EmbeddedChannel();

        // Increment active requests
        LeastConnectionsLoadBalancer.increment(ch1); // 1 active
        LeastConnectionsLoadBalancer.increment(ch1); // 2 active
        LeastConnectionsLoadBalancer.increment(ch2); // 1 active
        // ch3 has 0 active

        Channel selected = lb.select(List.of(ch1, ch2, ch3));
        assertEquals(ch3, selected, "Must select channel with minimum active requests (ch3)");

        // Increment ch3 to 2, decrement ch1
        LeastConnectionsLoadBalancer.increment(ch3);
        LeastConnectionsLoadBalancer.increment(ch3);
        LeastConnectionsLoadBalancer.decrement(ch1); // ch1 is now 1 active

        selected = lb.select(List.of(ch1, ch2, ch3));
        // ch1 and ch2 have 1 active, both are better than ch3 (which has 2)
        assertNotNull(selected);
        assertEquals(1, LeastConnectionsLoadBalancer.getActiveCount(selected));
    }
}
