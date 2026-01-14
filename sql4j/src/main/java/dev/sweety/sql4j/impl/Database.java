package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.impl.connection.ConnectionType;

import java.util.Collection;
import java.util.IdentityHashMap;

public class Database {

    private final IdentityHashMap<Class<?>, Repository<?>> repositories = new IdentityHashMap<>();
    private final ConnectionType connectionType;
    private final String[] params;

    private final Dialect dialect;

    public Database(final ConnectionType connectionType, final String... params) {
        this.connectionType = connectionType;
        this.params = params;
        this.dialect = this.getConnection().dialect();
    }

    public SqlConnection getConnection() {
        return connectionType.getConnection(params);
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
}
