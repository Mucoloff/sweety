package dev.sweety.ormlite.util;

import com.j256.ormlite.dao.Dao;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@AllArgsConstructor
public abstract class Repository<Entity extends Table<ID>, ID> {

    protected final Dao<Entity, ID> dao;

    @SneakyThrows
    public Entity create(final Entity entity) {
        return this.dao.createIfNotExists(entity);
    }

    @SneakyThrows
    public boolean exits(final ID id) {
        return this.dao.idExists(id);
    }

    public Entity findById(final ID id) throws SQLException {
        return this.dao.queryForId(id);
    }

    public CompletableFuture<Entity> updateAsync(final Entity stats) {
        return CompletableFuture.supplyAsync(() -> update(stats));
    }

    @SneakyThrows
    public Entity update(final Entity stats) {
        this.dao.createOrUpdate(stats);
        return stats;
    }

    @SneakyThrows
    public void clear() {
        for (Entity entity : this.dao) this.dao.delete(entity);
    }

    @SneakyThrows
    public List<Entity> findAll() {
        return this.dao.queryForAll();
    }

    @SneakyThrows
    public void delete(ID id) {
        this.dao.deleteById(id);
    }
}