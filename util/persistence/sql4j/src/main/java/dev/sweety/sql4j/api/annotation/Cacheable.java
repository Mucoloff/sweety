package dev.sweety.sql4j.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Opts an entity class into SQL4J's L2 Caffeine-backed cache.
 *
 * <p>Place this annotation on the entity class (not the repository interface):
 *
 * <pre>{@code
 * @Table.Info(name = "users")
 * @Cacheable(maxSize = 500, ttlSeconds = 300)  // 5-minute TTL, up to 500 entries
 * public class User { ... }
 * }</pre>
 *
 * <h3>Size vs TTL</h3>
 * Both constraints are enforced independently by Caffeine:
 * <ul>
 *   <li>{@link #maxSize()} — evicts the least-recently-used entry when the cache exceeds
 *       this size (LRU eviction). Default: {@code 1000}.</li>
 *   <li>{@link #ttlSeconds()} — evicts entries a fixed time after they were written,
 *       regardless of how recently they were read (write-based TTL). Default: {@code 0},
 *       meaning entries never expire due to age.</li>
 * </ul>
 *
 * <p>Cache invalidation still occurs on {@code insert}, {@code update}, {@code delete}, and
 * any method annotated with {@link CacheEvict @CacheEvict}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Cacheable {

    /**
     * Maximum number of entities to keep in the L2 cache for this type.
     * When the cache exceeds this number the least-recently-used entry is evicted.
     * A value {@code <= 0} uses the implementation default ({@code 1000}).
     */
    int maxSize() default -1;

    /**
     * Time-to-live in seconds for cache entries, measured from the time of writing
     * (i.e. after an insert, update, or cache-population read).
     *
     * <p>{@code 0} (the default) means entries do not expire due to age.
     * Use a positive value to limit staleness in high-write environments.
     *
     * @see #ttlUnit()
     */
    long ttlSeconds() default 0;

    /**
     * Unit of time for {@link #ttlSeconds()}.
     * Defaults to {@link TimeUnit#SECONDS}. Change to {@link TimeUnit#MINUTES} or
     * {@link TimeUnit#HOURS} for coarser-grained expiry without adjusting the numeric value.
     *
     * <p>Examples:
     * <pre>
     *   @Cacheable(ttlSeconds = 5,  ttlUnit = TimeUnit.MINUTES)  // 5 minutes
     *   @Cacheable(ttlSeconds = 2,  ttlUnit = TimeUnit.HOURS)    // 2 hours
     *   @Cacheable(ttlSeconds = 300)                              // 300 seconds (default unit)
     * </pre>
     */
    TimeUnit ttlUnit() default TimeUnit.SECONDS;
}
