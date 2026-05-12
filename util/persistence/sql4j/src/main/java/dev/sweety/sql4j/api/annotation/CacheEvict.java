package dev.sweety.sql4j.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link Query @Query} method in a repository interface as a <em>mutating</em>
 * operation that must evict entries from the entity's L2 cache after it completes.
 *
 * <p>Combine with {@code @Query} to tell the annotation processor to generate
 * cache-eviction logic alongside the SQL execution:
 *
 * <pre>{@code
 * @Query("DELETE FROM orders WHERE user_id = :userId")
 * @CacheEvict
 * CompletableFuture<Void> deleteByUser(long userId);
 * }</pre>
 *
 * <h3>Eviction scopes</h3>
 * <ul>
 *   <li>{@link Scope#ALL_ENTITIES} (default) — invalidates the entire L2 cache for
 *       this repository's entity type. Safe and simple; use when the mutation may
 *       touch an unknown number of rows.</li>
 *   <li>{@link Scope#BY_ID} — invalidates only the cache entry for a single primary
 *       key. The annotated method must have exactly one parameter whose name is
 *       {@code id} or {@code pk}. Faster when only one row is affected.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface CacheEvict {

    /** How much of the L2 cache to invalidate when this method completes. */
    Scope scope() default Scope.ALL_ENTITIES;

    enum Scope {
        /**
         * Clears every cached entry for this repository's entity class.
         * Generated code: {@code entityCache.evictAll(entityClass)}.
         */
        ALL_ENTITIES,

        /**
         * Clears only the entry whose primary key equals the {@code id} / {@code pk}
         * parameter of the method. Generated code: {@code entityCache.evict(entityClass, id)}.
         */
        BY_ID
    }
}
