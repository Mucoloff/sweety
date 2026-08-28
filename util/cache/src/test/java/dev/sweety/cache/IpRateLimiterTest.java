package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {

    private static IpAddress ip(String str) {
        if (str == null) return null;
        return IpAddress.of(str.getBytes(StandardCharsets.UTF_8), str);
    }

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        IpRateLimiter limiter = IpRateLimiter.create(3, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(ip("ip")));
        assertTrue(limiter.tryAcquire(ip("ip")));
        assertTrue(limiter.tryAcquire(ip("ip")));
        assertFalse(limiter.tryAcquire(ip("ip")), "bucket exhausted → reject");
    }

    @Test
    void distinctKeysHaveIndependentBuckets() {
        IpRateLimiter limiter = IpRateLimiter.create(1, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(ip("a")));
        assertFalse(limiter.tryAcquire(ip("a")));
        assertTrue(limiter.tryAcquire(ip("b")), "second key must not share the first key's bucket");
    }

    @Test
    void refillRestoresTokens() throws InterruptedException {
        IpRateLimiter limiter = IpRateLimiter.create(1, 1000.0, Duration.ofMinutes(1)); // 1000 tokens/s
        assertTrue(limiter.tryAcquire(ip("ip")));
        assertFalse(limiter.tryAcquire(ip("ip")));
        Thread.sleep(20); // ~20 tokens refilled, capped at capacity 1
        assertTrue(limiter.tryAcquire(ip("ip")), "token should have refilled");
    }

    @Test
    void nullOrBlankKeyNeverLimited() {
        IpRateLimiter limiter = IpRateLimiter.create(1, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(ip("")));
        assertTrue(limiter.tryAcquire(ip("   ")));
    }
}
