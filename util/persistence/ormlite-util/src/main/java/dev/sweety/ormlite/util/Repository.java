package dev.sweety.ormlite.util;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class Repository<Entity extends Table<ID>, ID> {

    protected final Dao<Entity, ID> dao;

    public Repository(final Dao<Entity, ID> dao) {
        this.dao = dao;
    }

    public Dao<Entity, ID> getDao() {
        return dao;
    }

    public Entity create(final Entity entity) {
        try {
            return this.dao.createIfNotExists(entity);
        } catch (SQLException e) {
            return throwUnchecked(e);
        }
    }

    public boolean exits(final ID id) {
        try {
            return this.dao.idExists(id);
        } catch (SQLException e) {
            return throwUnchecked(e);
        }
    }

    public Entity findById(final ID id) throws SQLException {
        return this.dao.queryForId(id);
    }

    public CompletableFuture<Entity> updateAsync(final Entity stats) {
        return CompletableFuture.supplyAsync(() -> update(stats));
    }

    public Entity update(final Entity stats) {
        try {
            this.dao.createOrUpdate(stats);
            return stats;
        } catch (SQLException e) {
            return throwUnchecked(e);
        }
    }

    public void clear() {
        try {
            for (Entity entity : this.dao) this.dao.delete(entity);
        } catch (SQLException e) {
            throwUnchecked(e);
        }
    }

    public List<Entity> findAll() {
        try {
            return this.dao.queryForAll();
        } catch (SQLException e) {
            return throwUnchecked(e);
        }
    }

    public void delete(ID id) {
        try {
            this.dao.deleteById(id);
        } catch (SQLException e) {
            throwUnchecked(e);
        }
    }

    private static <T, E extends Throwable> T throwUnchecked(final Throwable throwable) throws E {
        //noinspection unchecked
        throw (E) throwable;
    }
}