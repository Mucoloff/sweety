package dev.sweety.ormlite.util;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class Database {
    protected final Map<Class<?>, Repository<?, ?>> repositories = new HashMap<>();
    protected final ConnectionSource connectionSource;

    public Database(ConnectionType connectionType, String... params) {
        this.connectionSource = connectionType.getConnection(params);
    }


    public <T, ID> Dao<T, ID> createDao(Class<T> clazz) {
        try {
            TableUtils.createTableIfNotExists(this.connectionSource, clazz);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create table for " + clazz.getSimpleName(), e);
        }
        try {
            return DaoManager.createDao(this.connectionSource, clazz);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create DAO for " + clazz.getSimpleName(), e);
        }
    }

    public <REPO extends Repository<TABLE, ID>, TABLE extends Table<ID>, ID> REPO createRepository(Class<REPO> repo, Class<TABLE> table) {
        final Dao<TABLE, ID> dao = createDao(table);
        final REPO repository;
        try {
            repository = repo.getDeclaredConstructor(Dao.class).newInstance(dao);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        this.repositories.put(repo, repository);
        return repository;
    }

    public <REPO extends Repository<TABLE, ID>, TABLE extends Table<ID>, ID> REPO getRepository(Class<REPO> clazz) {
        if (!this.repositories.containsKey(clazz)) return null;
        // noinspection unchecked
        return (REPO) this.repositories.get(clazz);
    }

}