package dev.sweety.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * Per-key token-bucket rate limiter for exposed endpoints (key is typically the client IP).
 *
 * <p>Each key gets a bucket that holds up to {@code capacity} tokens and refills continuously at
 * {@code refillPerSecond}. {@link #tryAcquire(IpAddress)} consumes one token, returning {@code false}
 * when the bucket is empty — that is the signal to reject the request. Buckets live in a Caffeine
 * cache with {@code expireAfterAccess} eviction, so memory stays bounded no matter how many distinct
 * keys are seen and idle clients are forgotten automatically.</p>
 *
 * <p>Bursts up to {@code capacity} are allowed; sustained throughput is capped at {@code refillPerSecond}.</p>
 */
public final class IpRateLimiter {

    private final Cache<IpAddress, Bucket> buckets;
    private final double capacity;
    private final double refillPerNano;

    private IpRateLimiter(double capacity, double refillPerSecond, Duration idleEviction) {
        this.capacity      = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(idleEviction)
                .maximumSize(1_000_000)
                .build();
    }

    /**
     * @param capacity        maximum burst (token bucket size)
     * @param refillPerSecond sustained allowed rate (tokens added per second)
     * @param idleEviction    drop a key's bucket after this much inactivity
     */
    public static IpRateLimiter create(int capacity, double refillPerSecond, Duration idleEviction) {
        if (capacity <= 0)        throw new IllegalArgumentException("capacity must be positive: " + capacity);
        if (refillPerSecond <= 0) throw new IllegalArgumentException("refillPerSecond must be positive: " + refillPerSecond);
        if (idleEviction == null || idleEviction.isNegative() || idleEviction.isZero()) {
            throw new IllegalArgumentException("idleEviction must be positive");
        }
        return new IpRateLimiter(capacity, refillPerSecond, idleEviction);
    }

    /**
     * Attempts to consume one token for {@code key}.
     *
     * @return {@code true} if a token was available (allow the request); {@code false} if rate-limited.
     *         A null/blank key is never limited (cannot be attributed).
     */
    public boolean tryAcquire(IpAddress key) {
        if (key == null || key.isBlank()) return true;
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, System.nanoTime()));
        return bucket.tryConsume(refillPerNano, capacity, System.nanoTime());
    }

    private static final class Bucket {
        private double tokens;
        private long   lastRefillNanos;

        Bucket(double tokens, long nowNanos) {
            this.tokens          = tokens;
            this.lastRefillNanos = nowNanos;
        }

        synchronized boolean tryConsume(double refillPerNano, double capacity, long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                lastRefillNanos = nowNanos;
            }

            if (tokens < 1) return false;

            tokens -= 1.0;
            return true;
        }
    }
}
