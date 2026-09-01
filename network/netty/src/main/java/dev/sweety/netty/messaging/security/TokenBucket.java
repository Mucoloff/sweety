package dev.sweety.netty.messaging.security;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free, nano-precision Token Bucket rate limiter.
 * Ideal for high-throughput Netty I/O threads without lock contention.
 */
public final class TokenBucket {

    private final long capacity;
    private final double refillTokensPerNano;

    // High 32-bits: available tokens (fixed-point or integer), Low 32-bits or combined in state
    private final AtomicLong availableTokens;
    private final AtomicLong lastRefillNanos;

    /**
     * @param capacity Maximum burst capacity of tokens in the bucket.
     * @param refillTokensPerSecond Token replenishment rate per second.
     */
    public TokenBucket(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (refillTokensPerSecond <= 0) throw new IllegalArgumentException("Refill rate must be positive");
        this.capacity = capacity;
        this.refillTokensPerNano = refillTokensPerSecond / 1_000_000_000.0;
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    public static TokenBucket of(long capacity, double refillTokensPerSecond) {
        return new TokenBucket(capacity, refillTokensPerSecond);
    }

    /**
     * Attempts to consume 1 token.
     * @return {@code true} if consumed, {@code false} if rate limit exceeded.
     */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /**
     * Attempts to consume {@code tokensToConsume} tokens.
     * @param tokensToConsume Number of tokens to consume.
     * @return {@code true} if consumed, {@code false} if rate limit exceeded.
     */
    public boolean tryConsume(long tokensToConsume) {
        if (tokensToConsume <= 0) return true;
        refill();

        while (true) {
            long currentTokens = availableTokens.get();
            if (currentTokens < tokensToConsume) {
                return false;
            }
            if (availableTokens.compareAndSet(currentTokens, currentTokens - tokensToConsume)) {
                return true;
            }
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsedNanos = now - last;
        if (elapsedNanos <= 0) return;

        if (lastRefillNanos.compareAndSet(last, now)) {
            long tokensToAdd = (long) (elapsedNanos * refillTokensPerNano);
            if (tokensToAdd > 0) {
                while (true) {
                    long current = availableTokens.get();
                    long updated = Math.min(capacity, current + tokensToAdd);
                    if (availableTokens.compareAndSet(current, updated)) {
                        break;
                    }
                }
            }
        }
    }

    public long getAvailableTokens() {
        refill();
        return availableTokens.get();
    }

    public long getCapacity() {
        return capacity;
    }
}
