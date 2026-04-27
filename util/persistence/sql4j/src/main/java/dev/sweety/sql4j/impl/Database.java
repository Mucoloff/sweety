package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import dev.sweety.sql4j.api.query.chain.QueryChain;
import dev.sweety.sql4j.impl.transaction.TransactionManager;
import dev.sweety.sql4j.api.obj.table.TableRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class Database implements AutoCloseable {

    private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
    private final TableRegistry tableRegistry = new TableRegistry();
    private final SqlConnection connection;
    private final Dialect dialect;
    private final TransactionManager transactionManager;

    public Database(final SqlConnection connection) {
        this.connection = connection;
        this.dialect = connection.dialect();
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
        return getOrCreateRepository(entityClass, clazz -> new Repository<>(tableRegistry.get(clazz)));
    }

    public <R extends Repository<E>, E> R createRepository(final Class<E> entityClass, String customTableName) {
        return getOrCreateRepository(entityClass, clazz -> new Repository<>(tableRegistry.getOrCreate(clazz, customTableName)));
    }

    public <R extends Repository<E>, E> R getOrCreateRepository(final Class<E> entityClass, Function<Class<E>, Repository<E>> factory) {
        //noinspection unchecked
        return (R) repositories.computeIfAbsent(entityClass, k -> factory.apply((Class<E>) k));
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

    public CompletableFuture<Void> transaction(final TransactionManager.TransactionBlock block) {
        return transactionManager.transaction(block);
    }

    @Override
    public void close() {
        connection.close();
    }
}
