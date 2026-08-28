package dev.sweety.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * Sizing and TTL parameters for a {@link ReadThroughGuard}.
 *
 * @param bloomInitialBuckets initial bucket-vector length of the counting bloom filter
 * @param bloomGrowthFactor   multiplier applied when the bloom auto-expands (&gt; 1)
 * @param bloomHashCount      number of hash functions in the bloom
 * @param maxCacheSize        maximum number of cache entries before Caffeine evicts
 * @param valueTtlNanos       TTL for a cached present value
 * @param absentTtlNanos      TTL for a negatively-cached absent key (usually shorter, so newly-created
 *                            rows become visible quickly)
 */
public record GuardConfig(int bloomInitialBuckets,
                          double bloomGrowthFactor,
                          int bloomHashCount,
                          long maxCacheSize,
                          long valueTtlNanos,
                          long absentTtlNanos) {

    public GuardConfig {
        if (bloomInitialBuckets <= 0) throw new IllegalArgumentException("bloomInitialBuckets must be positive");
        if (bloomGrowthFactor <= 1) throw new IllegalArgumentException("bloomGrowthFactor must be > 1");
        if (bloomHashCount <= 0)      throw new IllegalArgumentException("bloomHashCount must be positive");
        if (maxCacheSize <= 0)        throw new IllegalArgumentException("maxCacheSize must be positive");
        if (valueTtlNanos <= 0)       throw new IllegalArgumentException("valueTtlNanos must be positive");
        if (absentTtlNanos <= 0)      throw new IllegalArgumentException("absentTtlNanos must be positive");
    }

    /**
     * Sensible defaults for a per-table guard: 8192 buckets, ×2 growth, 4 hashes, 50k entries,
     * {@code valueTtl} for hits and {@code absentTtl} for negative caching.
     */
    public static GuardConfig of(Duration valueTtl, Duration absentTtl) {
        Objects.requireNonNull(valueTtl, "valueTtl");
        Objects.requireNonNull(absentTtl, "absentTtl");
        return new GuardConfig(8192, 2.0, 4, 50_000, valueTtl.toNanos(), absentTtl.toNanos());
    }
}
