package dev.sweety.cache;

import dev.sweety.filter.CountMinSketch;

import java.nio.charset.StandardCharsets;

/**
 * Fixed-memory DDoS and volumetric request rate-limiter backed by {@link CountMinSketch}.
 *
 * <p>Unlike bucket-per-IP limiters that allocate hashmap entries and bucket objects per client IP,
 * this limiter operates in a fixed footprint (e.g. 4 rows x 4096 buckets = 64 KB).
 *
 * <p>Supports periodic sliding-window decay via {@link CountMinSketch#age()} or reset.
 */
public final class CountMinSketchRateLimiter {

    private volatile CountMinSketch sketch;
    private final int bucketCount;
    private final int hashFunctionCount;
    private final int maxPerWindow;
    private volatile long windowStartNanos;
    private final long windowDurationNanos;

    private CountMinSketchRateLimiter(int bucketCount, int hashFunctionCount, int maxPerWindow, long windowDurationMillis) {
        this.bucketCount = bucketCount;
        this.hashFunctionCount = hashFunctionCount;
        this.sketch = CountMinSketch.of(bucketCount, hashFunctionCount);
        this.maxPerWindow = maxPerWindow;
        this.windowDurationNanos = windowDurationMillis * 1_000_000L;
        this.windowStartNanos = System.nanoTime();
    }

    public static CountMinSketchRateLimiter create(int bucketCount, int hashFunctionCount, int maxPerWindow, long windowDurationMillis) {
        if (bucketCount <= 0) throw new IllegalArgumentException("bucketCount must be positive: " + bucketCount);
        if (hashFunctionCount <= 0) throw new IllegalArgumentException("hashFunctionCount must be positive: " + hashFunctionCount);
        if (maxPerWindow <= 0) throw new IllegalArgumentException("maxPerWindow must be positive: " + maxPerWindow);
        if (windowDurationMillis <= 0) throw new IllegalArgumentException("windowDurationMillis must be positive: " + windowDurationMillis);
        return new CountMinSketchRateLimiter(bucketCount, hashFunctionCount, maxPerWindow, windowDurationMillis);
    }

    /**
     * Default constructor for standard 64 KB DDoS rate limiter: 4096 buckets, 4 hashers, 1-second window.
     */
    public static CountMinSketchRateLimiter createDefault(int maxPerSecond) {
        return create(4096, 4, maxPerSecond, 1000L);
    }

    /**
     * Attempts to acquire one unit for the specified key.
     *
     * @param key String representation (IP address or userId)
     * @return {@code true} if within limits (allowed); {@code false} if rate-limited (reject).
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) return true;
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return tryAcquire(bytes, 1);
    }

    /**
     * Attempts to acquire {@code weight} units for the specified raw byte key.
     */
    public synchronized boolean tryAcquire(byte[] keyBytes, int weight) {
        if (keyBytes == null || keyBytes.length == 0) return true;
        checkWindowRoll();

        for (int i = 0; i < weight; i++) {
            sketch.add(keyBytes);
        }
        int currentEstimate = sketch.estimate(keyBytes);
        return currentEstimate <= maxPerWindow;
    }

    private void checkWindowRoll() {
        long now = System.nanoTime();
        if (now - windowStartNanos >= windowDurationNanos) {
            windowStartNanos = now;
            sketch = CountMinSketch.of(bucketCount, hashFunctionCount);
        }
    }
}
