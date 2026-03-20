package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.configuration.DatabaseConfig;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Database implements AutoCloseable {

    private final IdentityHashMap<Class<?>, Repository<?>> repositories = new IdentityHashMap<>();
    private final SqlConnection connection;
    private final Dialect dialect;

    public Database(final SqlConnection connection) {
        this.connection = connection;
        this.dialect = connection.dialect();
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
        final Repository<E> repo = new Repository<>(entityClass);
        this.repositories.put(entityClass, repo);
        //noinspection unchecked
        return (R) repo;
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

    @Override
    public void close() {
        connection.close();
    }
}
