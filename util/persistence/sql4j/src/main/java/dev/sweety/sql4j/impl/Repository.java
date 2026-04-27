package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.QueryCache;

import dev.sweety.sql4j.impl.query.SelectJoin;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.util.function.Supplier;

public record Repository<Entity>(Table<Entity> table) {

    public Repository(Table<Entity> table) {
        this.table = table;
    }

    /**
     * @deprecated Use {@link Database#createRepository(Class)} instead to ensure proper registry isolation.
     */
    @Deprecated
    public Repository(final Class<Entity> table) {
        this(new TableRegistry().get(table));
    }

    public InsertEntity<Entity> insert(Entity entity) {
        return Query.insert(table, entity);
    }

    public UpdateEntity<Entity> update(Entity entity) {
        return Query.update(table, entity);
    }

    public DeleteEntity<Entity> delete(Entity entity) {
        return Query.delete(table, entity);
    }

    public SelectEntity<Entity> selectAll() {
        return Query.selectAll(table);
    }

    public SelectEntity<Entity> selectWhere(String where, Object... params) {
        return new SelectEntity<>(table, where, params);
    }

    public CreateTable create(Dialect dialect, boolean ifNotExists) {
        return new CreateTable(this.table, dialect, ifNotExists);
    }

    public DropTable dropTable() {
        return new DropTable(this.table);
    }

    public static <T> Query<T> cached(String key, Supplier<Query<T>> supplier) {
        return QueryCache.getQuery(key, _ -> supplier.get());
    }

    public static SelectJoin.Builder join(Table<?>... tables) {
        return new SelectJoin.Builder().join(tables);
    }
}

