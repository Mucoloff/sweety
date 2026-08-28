package dev.sweety.sql4j.api.shard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the sharding partition key field (typically {@code userId}, {@code accountId}, or {@code tenantId})
 * used by {@link VirtualShardRouter} to deterministically route queries to physical database partitions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ShardKey {
}
