package dev.sweety.netty.routing;

import org.jetbrains.annotations.NotNull;

/**
 * Functional extractor to resolve 64-bit partition/shard keys from messages or requests.
 * Used for database sharding (SQL4J RPC) and sticky-session routing.
 *
 * @param <M> packet or message type
 */
@FunctionalInterface
public interface ShardKeyExtractor<M> {

    /**
     * Extracts a 64-bit partition key from the message.
     *
     * @param message non-null message
     * @return 64-bit shard key
     */
    long extractKey(@NotNull M message);
}
