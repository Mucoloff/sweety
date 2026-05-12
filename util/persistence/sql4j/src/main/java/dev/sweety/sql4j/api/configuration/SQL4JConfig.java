package dev.sweety.sql4j.api.configuration;

import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Immutable, inspectable snapshot of all configuration resolved by the SQL4J builder.
 *
 * <p>Credentials are intentionally masked in {@link #toString()} so that logging this
 * object never leaks passwords.
 *
 * <p>Obtain an instance via {@code SQL4J.connect().<driver>(...).build()} or
 * {@code SQL4J.connect().<driver>(...).open()} (which builds internally).
 */
public record SQL4JConfig(
        DialectType dialect,
        String jdbcUrl,
        @Nullable String user,
        @Nullable String password,
        boolean useHikari,
        HikariTuning hikariTuning,
        boolean cacheEnabled,
        Executor executor,
        /**
         * Maximum number of rows per {@code executeBatch()} call.
         * {@code 0} (default) means all rows are batched in a single call.
         * Use a positive value (e.g. {@code 500}) to avoid oversized batches
         * that can exhaust driver memory or exceed server packet limits.
         */
        int batchChunkSize
) {

    /**
     * Immutable snapshot of HikariCP connection-pool tuning parameters.
     *
     * <p>Use {@link #defaults()} to obtain a copy initialised to HikariCP's defaults.
     *
     * @param maxPoolSize        Maximum number of connections in the pool (default 10).
     * @param minIdle            Minimum number of idle connections (default same as maxPoolSize).
     * @param connectionTimeout  Maximum time to wait for a connection from the pool (default 30 s).
     * @param idleTimeout        Maximum time a connection is allowed to sit idle (default 10 min).
     * @param maxLifetime        Maximum lifetime of a connection in the pool (default 30 min).
     */
    public record HikariTuning(
            int maxPoolSize,
            int minIdle,
            Duration connectionTimeout,
            Duration idleTimeout,
            Duration maxLifetime
    ) {
        /** Returns a {@code HikariTuning} initialised to HikariCP's built-in defaults. */
        public static HikariTuning defaults() {
            return new HikariTuning(
                    10,
                    10,
                    Duration.ofSeconds(30),
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(30)
            );
        }
    }

    /**
     * Returns a human-readable representation of this configuration.
     * The {@code password} field is masked as {@code "****"} regardless of its actual value.
     */
    @Override
    public String toString() {
        return "SQL4JConfig[dialect=%s, jdbcUrl=%s, user=%s, password=%s, useHikari=%s, cache=%s, maxPoolSize=%d, batchChunkSize=%d]"
                .formatted(
                        dialect,
                        jdbcUrl,
                        user,
                        maskPassword(password),
                        useHikari,
                        cacheEnabled,
                        hikariTuning != null ? hikariTuning.maxPoolSize() : -1,
                        batchChunkSize
                );
    }

    private static String maskPassword(@Nullable String password) {
        return password == null ? null : "****";
    }
}
