package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpRateLimiterTest {

    private static IpAddress ip(String str) {
        if (str == null) return null;
        return IpAddress.parse(str);
    }

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        IpRateLimiter limiter = IpRateLimiter.create(3, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(ip("192.168.1.1")));
        assertTrue(limiter.tryAcquire(ip("192.168.1.1")));
        assertTrue(limiter.tryAcquire(ip("192.168.1.1")));
        assertFalse(limiter.tryAcquire(ip("192.168.1.1")), "bucket exhausted → reject");
    }

    @Test
    void distinctKeysHaveIndependentBuckets() {
        IpRateLimiter limiter = IpRateLimiter.create(1, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(ip("10.0.0.1")));
        assertFalse(limiter.tryAcquire(ip("10.0.0.1")));
        assertTrue(limiter.tryAcquire(ip("10.0.0.2")), "second key must not share the first key's bucket");
    }

    @Test
    void refillRestoresTokens() throws InterruptedException {
        IpRateLimiter limiter = IpRateLimiter.create(1, 1000.0, Duration.ofMinutes(1)); // 1000 tokens/s
        assertTrue(limiter.tryAcquire(ip("172.16.0.1")));
        assertFalse(limiter.tryAcquire(ip("172.16.0.1")));
        Thread.sleep(20); // ~20 tokens refilled, capped at capacity 1
        assertTrue(limiter.tryAcquire(ip("172.16.0.1")), "token should have refilled");
    }

    @Test
    void nullOrBlankKeyNeverLimited() {
        IpRateLimiter limiter = IpRateLimiter.create(1, 0.0001, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire(null));
        assertTrue(limiter.tryAcquire(ip("")));
        assertTrue(limiter.tryAcquire(ip("   ")));
    }
}
