package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import dev.sweety.sql4j.api.query.chain.QueryChain;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.transaction.TransactionManager;
import dev.sweety.sql4j.api.obj.table.TableRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class Database implements AutoCloseable {

    private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
    private final TableRegistry tableRegistry = TableRegistry.getDefault();
    private final QueryCache queryCache = new QueryCache();
    private final SqlConnection connection;
    private final Dialect dialect;
    private final TransactionManager transactionManager;

    public Database(final SqlConnection connection) {
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
        this.dialect = Objects.requireNonNull(connection.dialect(), "dialect cannot be null");
        this.transactionManager = new TransactionManager(connection);
    }

    public Database(final DatabaseConfig config, final Executor executor) {
        this(ConnectionType.valueOf(config.dialectType().name()).create(config, executor));
    }

    /**
     * @deprecated Use {@link #Database(DatabaseConfig, Executor)} or {@link #Database(SqlConnection)} instead.
     */
    @Deprecated
    public Database(final ConnectionType connectionType, final String... params) {
        this(connectionType.create(Executors.newCachedThreadPool(), params));
    }

    public SqlConnection getConnection() {
        return connection;
    }

    public <R extends Repository<E>, E> R createRepository(final Class<E> entityClass) {
        return getOrCreateRepository(entityClass,
                clazz -> new Repository<>(tableRegistry.get(clazz), dialect, queryCache, tableRegistry));
    }

    public <R extends Repository<E>, E> R createRepository(final Class<E> entityClass, String customTableName) {
        return getOrCreateRepository(entityClass,
                clazz -> new Repository<>(tableRegistry.getOrCreate(clazz, customTableName), dialect, queryCache, tableRegistry));
    }

    public <R extends Repository<E>, E> R getOrCreateRepository(final Class<E> entityClass,
                                                                  Function<Class<E>, Repository<E>> factory) {
        //noinspection unchecked
        return (R) repositories.computeIfAbsent(entityClass, k -> {
            Repository<E> repo = factory.apply((Class<E>) k);
            migrateAll();
            return repo;
        });
    }

    public void migrateAll() {
        for (dev.sweety.sql4j.api.obj.Table<?> t : tableRegistry.allTables()) {
            new dev.sweety.sql4j.impl.query.table.CreateTable(t, dialect, true).execute(connection).join();
            for (String indexSql : dev.sweety.sql4j.impl.query.table.CreateTable.buildIndices(t, true)) {
                connection.executeAsync(new dev.sweety.sql4j.api.query.AbstractQuery<Void>() {
                    @Override protected String buildSql() { return indexSql; }
                    @Override public void bind(java.sql.PreparedStatement ps) {}
                    @Override public Void execute(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        ps.execute();
                        return null;
                    }
                }).join();
            }
            new Repository<>(t, dialect, queryCache, tableRegistry).migrateSchema(connection);
        }
    }

    public TableRegistry tableRegistry() {
        return tableRegistry;
    }

    public <R extends Repository<E>, E> R getRepository(final Class<E> clazz) {
        //noinspection unchecked
        return (R) repositories.get(clazz);
    }

    public Dialect dialect() {
        return dialect;
    }

    public Collection<Repository<?>> repositories() {
        return repositories.values();
    }

    public <T> CompletableFuture<T> transaction(final QueryChain<T> chain) {
        return transactionManager.transaction(chain);
    }

    /**
     * Executes a transactional block with savepoint support.
     * Use this for complex transactions that require savepoints or conditional rollbacks.
     *
     * <pre>{@code
     * db.transact(tx -> {
     *     tx.execute(repo.insert(entity));
     *     tx.savepoint("after_insert");
     *     try {
     *         tx.execute(riskyQuery);
     *     } catch (SQLException e) {
     *         tx.rollbackTo("after_insert");
     *     }
     * }).join();
     * }</pre>
     */
    public CompletableFuture<Void> transact(final TransactionManager.TransactionBlock block) {
        return transactionManager.transaction(block);
    }

    @Override
    public void close() {
        connection.close();
    }
}
