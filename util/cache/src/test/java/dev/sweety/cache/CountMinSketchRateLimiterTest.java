package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountMinSketchRateLimiterTest {

    @Test
    void testRateLimiterAllowAndReject() {
        CountMinSketchRateLimiter limiter = CountMinSketchRateLimiter.create(1024, 4, 5, 1000L);
        String ip = "192.168.1.100";

        // First 5 requests should pass
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(ip), "Request " + (i + 1) + " should pass");
        }

        // 6th request should be rejected
        assertFalse(limiter.tryAcquire(ip), "Request 6 should exceed rate limit");
    }

    @Test
    void testDebitAccumulator() {
        FastutilDebitAccumulator accumulator = new FastutilDebitAccumulator();
        accumulator.recordDebit(1001L, 10);
        accumulator.recordDebit(1001L, 15);
        accumulator.recordDebit(2002L, 5);

        assertTrue(accumulator.getPending(1001L) == 25);
        assertTrue(accumulator.getPending(2002L) == 5);
        assertTrue(accumulator.getPending(3003L) == 0);

        final int[] drainedUsers = {0};
        final int[] totalDebited = {0};

        accumulator.drain((userId, units) -> {
            drainedUsers[0]++;
            totalDebited[0] += units;
        });

        assertTrue(drainedUsers[0] == 2);
        assertTrue(totalDebited[0] == 30);
        assertTrue(accumulator.isEmpty());
    }
}
