package dev.sweety.core.persistence.sql;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Database {
    protected final Map<Class<?>, Repository<?, ?>> repositories = new HashMap<>();
    protected final ConnectionSource connectionSource;

    public Database(ConnectionType connectionType, String... params) {
        this.connectionSource = connectionType.getConnection(params);
    }

    @SneakyThrows
    public <T, ID> Dao<T, ID> createDao(Class<T> clazz) {
        TableUtils.createTableIfNotExists(this.connectionSource, clazz);
        return DaoManager.createDao(this.connectionSource, clazz);
    }

    @SneakyThrows
    public <REPO extends Repository<TABLE, ID>, TABLE extends Table<ID>, ID> REPO createRepository(Class<REPO> repo, Class<TABLE> table) {
        final Dao<TABLE, ID> dao = createDao(table);
        final REPO repository = repo.getDeclaredConstructor(Dao.class).newInstance(dao);
        this.repositories.put(repo, repository);
        return repository;
    }

    public <REPO extends Repository<TABLE, ID>, TABLE extends Table<ID>, ID> REPO getRepository(Class<REPO> clazz) {
        if (!this.repositories.containsKey(clazz)) return null;
        // noinspection unchecked
        return (REPO) this.repositories.get(clazz);
    }

}