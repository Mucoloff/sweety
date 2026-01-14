package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.Dialect;
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
import dev.sweety.sql4j.impl.query.param.ParamQuery;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public record Repository<Entity>(Table<Entity> table) {

    public Repository(Table<Entity> table) {
        TableRegistry.register(this.table = table);
    }

    private static <T> String name(final Class<T> table) {
        final Table.Info tableInfo = table.getAnnotation(Table.Info.class);
        if (tableInfo == null) throw new IllegalStateException("Missing @Table on " + table);
        return tableInfo.name();
    }

    public Repository(final Class<Entity> table) {
        this(new Table<>(table, name(table)));
    }

    public static <T> Query<T> cached(
            String key,
            Supplier<Query<T>> supplier
    ) {
        return QueryCache.get(key, supplier);
    }


    public SelectEntity<Entity> selectAll() {
        return new SelectEntity<>(this.table);
    }

    public SelectEntity<Entity> selectWhere(final String whereClause, final Object... params) {
        return new SelectEntity<>(this.table, whereClause, params);
    }

    public static SelectJoin.Builder join(Table<?>... tables) {
        return new SelectJoin.Builder().join(tables);
    }

    @SafeVarargs
    public final InsertEntity<Entity> insert(Entity... instances) {
        return new InsertEntity<>(table, instances);
    }

    public UpdateEntity<Entity> update(Entity instance) {
        return new UpdateEntity<>(table, instance);
    }

    @SafeVarargs
    public final DeleteEntity<Entity> delete(Entity... instances) {
        return new DeleteEntity<>(table, instances);
    }

    public CreateTable create(Dialect dialect, boolean ifNotExists) {
        return new CreateTable(this.table, dialect, ifNotExists);
    }

    public DropTable dropTable() {
        return new DropTable(this.table);
    }


    public static <T> CompletableFuture<T> execute(final SqlConnection connection, final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return generate(query, bind, execute).execute(connection);
    }

    public static <T> Query<T> generate(final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return ParamQuery.<T>builder().sql(query).bind(bind).execute(execute).build();
    }
}
