package dev.sweety.sql4j;

import com.zaxxer.hikari.HikariConfig;
import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.configuration.SQL4JConfig;
import dev.sweety.sql4j.api.exception.Sql4jConnectionException;
import dev.sweety.sql4j.impl.Database;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Entry point for building a SQL4J {@link Database} connection.
 *
 * <p>Usage (step builder, compile-time safe):
 * <pre>{@code
 * Database db = SQL4J.connect()
 *         .mysql("localhost", 3306, "mydb", "root", "secret")
 *         .pool(p -> p.maxPoolSize(20).connectionTimeoutSeconds(10))
 *         .open();
 * }</pre>
 *
 * <p>Calling {@code SQL4J.connect().open()} does <em>not</em> compile because
 * {@link DriverStep} does not declare {@code open()} — the driver step must be
 * resolved first.
 */
public final class SQL4J {

    private SQL4J() {}

    /**
     * Starts the step builder. The returned {@link DriverStep} forces callers to
     * pick a database dialect before any other option — {@code open()} is not
     * reachable at this stage.
     */
    public static DriverStep connect() {
        return new Builder();
    }

    // ─── Step interfaces ────────────────────────────────────────────────────────

    /**
     * First step: choose a database dialect. Returns {@link OpenStep} once a
     * dialect has been selected.
     */
    public interface DriverStep {
        /** Connect to an SQLite database file at the given path. */
        OpenStep sqlite(String path);
        /** Connect to an H2 database file at the given path. */
        OpenStep h2(String path, String user, String password);
        /** Connect to a MySQL server. */
        OpenStep mysql(String host, int port, String database, String user, String password);
        /** Connect to a PostgreSQL server. */
        OpenStep postgres(String host, int port, String database, String user, String password);
        /** Connect to a MariaDB server. */
        OpenStep mariadb(String host, int port, String database, String user, String password);
        /**
         * Use a pre-built, fully-resolved {@link SQL4JConfig}.
         * Useful for programmatic construction or testing.
         */
        OpenStep custom(SQL4JConfig config);
    }

    /**
     * Second step: optional tuning, then open the database.
     * {@code open()} is only reachable after a driver method on {@link DriverStep}.
     */
    public interface OpenStep {
        /** Override the async executor used for query dispatch. */
        OpenStep executor(Executor executor);
        /** Enable or disable the entity cache (default: {@code true}). */
        OpenStep withCache(boolean useCache);
        /** Tune HikariCP pool parameters via a fluent sub-builder. */
        OpenStep pool(Consumer<HikariTuningBuilder> tuning);
        /**
         * Provide a fully pre-configured {@link HikariConfig} for power-user scenarios.
         * When set, the individual pool/driver parameters are ignored in favour of this config.
         */
        OpenStep withHikariConfig(HikariConfig hikariConfig);
        /**
         * Split batch operations ({@code insertBatch}, {@code updateBatch}) into chunks of
         * this size. {@code 0} (default) means all rows in a single {@code executeBatch()}
         * call. A value of e.g. {@code 500} prevents oversized batches on MySQL/MariaDB.
         */
        OpenStep batchChunkSize(int size);
        /** Build and return the resolved {@link SQL4JConfig} without opening a connection. */
        SQL4JConfig build();
        /** Build the config and open a {@link Database} connection. */
        Database open();
    }

    // ─── Fluent pool sub-builder ────────────────────────────────────────────────

    /** Fluent builder for {@link SQL4JConfig.HikariTuning} parameters. */
    public static final class HikariTuningBuilder {
        private int maxPoolSize = 10;
        private int minIdle = 10;
        private long connectionTimeoutMs = 30_000;
        private long idleTimeoutMs = 600_000;
        private long maxLifetimeMs = 1_800_000;

        /** Maximum number of connections in the pool. */
        public HikariTuningBuilder maxPoolSize(int value) { this.maxPoolSize = value; return this; }
        /** Minimum number of idle connections. */
        public HikariTuningBuilder minIdle(int value) { this.minIdle = value; return this; }
        /** Maximum milliseconds to wait for a connection. */
        public HikariTuningBuilder connectionTimeoutMs(long ms) { this.connectionTimeoutMs = ms; return this; }
        /** Convenience: {@link #connectionTimeoutMs} in seconds. */
        public HikariTuningBuilder connectionTimeoutSeconds(long sec) { return connectionTimeoutMs(sec * 1_000); }
        /** Maximum milliseconds a connection may sit idle. */
        public HikariTuningBuilder idleTimeoutMs(long ms) { this.idleTimeoutMs = ms; return this; }
        /** Maximum lifetime in milliseconds of any connection. */
        public HikariTuningBuilder maxLifetimeMs(long ms) { this.maxLifetimeMs = ms; return this; }

        SQL4JConfig.HikariTuning build() {
            return new SQL4JConfig.HikariTuning(
                    maxPoolSize, minIdle,
                    java.time.Duration.ofMillis(connectionTimeoutMs),
                    java.time.Duration.ofMillis(idleTimeoutMs),
                    java.time.Duration.ofMillis(maxLifetimeMs)
            );
        }
    }

    // ─── Internal builder implementation ────────────────────────────────────────

    private static final class Builder implements DriverStep, OpenStep {

        private SQL4JConfig partialConfig;
        @Nullable private HikariConfig rawHikariConfig;
        private Executor executor;
        private boolean useCache = true;
        private SQL4JConfig.HikariTuning hikariTuning = SQL4JConfig.HikariTuning.defaults();
        private int batchChunkSize = 0;

        // --- DriverStep ---

        @Override
        public OpenStep sqlite(String path) {
            partialConfig = baseConfig(DatabaseConfig.sqlite(path), DialectType.SQLITE);
            return this;
        }

        @Override
        public OpenStep h2(String path, String user, String password) {
            partialConfig = baseConfig(DatabaseConfig.h2(path, user, password), DialectType.H2);
            return this;
        }

        @Override
        public OpenStep mysql(String host, int port, String database, String user, String password) {
            partialConfig = baseConfig(DatabaseConfig.mysql(host, port, database, user, password), DialectType.MYSQL);
            return this;
        }

        @Override
        public OpenStep postgres(String host, int port, String database, String user, String password) {
            partialConfig = baseConfig(DatabaseConfig.postgresql(host, port, database, user, password), DialectType.POSTGRESQL);
            return this;
        }

        @Override
        public OpenStep mariadb(String host, int port, String database, String user, String password) {
            partialConfig = baseConfig(DatabaseConfig.mariadb(host, port, database, user, password), DialectType.MARIADB);
            return this;
        }

        @Override
        public OpenStep custom(SQL4JConfig config) {
            this.partialConfig = config;
            return this;
        }

        // --- OpenStep ---

        @Override
        public OpenStep executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        @Override
        public OpenStep withCache(boolean useCache) {
            this.useCache = useCache;
            return this;
        }

        @Override
        public OpenStep pool(Consumer<HikariTuningBuilder> tuning) {
            HikariTuningBuilder b = new HikariTuningBuilder();
            tuning.accept(b);
            this.hikariTuning = b.build();
            return this;
        }

        @Override
        public OpenStep withHikariConfig(HikariConfig hikariConfig) {
            this.rawHikariConfig = hikariConfig;
            return this;
        }

        @Override
        public OpenStep batchChunkSize(int size) {
            this.batchChunkSize = size;
            return this;
        }

        @Override
        public SQL4JConfig build() {
            if (partialConfig == null) {
                throw new Sql4jConnectionException(
                        "No database configured — call sqlite(), h2(), mysql(), postgres(), or mariadb() before build()");
            }
            return new SQL4JConfig(
                    partialConfig.dialect(),
                    partialConfig.jdbcUrl(),
                    partialConfig.user(),
                    partialConfig.password(),
                    rawHikariConfig == null,
                    hikariTuning,
                    useCache,
                    executor,
                    batchChunkSize
            );
        }

        @Override
        public Database open() {
            SQL4JConfig config = build();
            if (rawHikariConfig != null) {
                // Power-user path: bypass SQL4JConfig-based provider construction
                dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider provider =
                        new dev.sweety.sql4j.impl.connection.provider.HikariConnectionProvider(rawHikariConfig);
                Executor exec = executor != null ? executor : Executors.newCachedThreadPool();
                dev.sweety.sql4j.api.connection.SqlConnection conn =
                        new dev.sweety.sql4j.api.connection.SqlConnection(config.dialect(), provider, exec, executor == null);
                Database db = new Database(conn);
                db.entityCache().setEnabled(useCache);
                return db;
            }
            Database db = new Database(config);
            return db;
        }

        // --- helpers ---

        private static SQL4JConfig baseConfig(DatabaseConfig cfg, DialectType dialectType) {
            return new SQL4JConfig(
                    dialectType,
                    cfg.jdbcUrl(),
                    cfg.user(),
                    cfg.password(),
                    true,
                    SQL4JConfig.HikariTuning.defaults(),
                    true,
                    null,
                    0
            );
        }
    }
}
