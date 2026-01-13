package dev.sweety.sql4j.impl.query.util;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.functions.QueryBinder;
import dev.sweety.sql4j.api.query.functions.QueryExecutor;
import dev.sweety.sql4j.impl.query.param.ParamQuery;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

public class QueryBuilder<Entity> {

    private final Table<Entity> table;

    public QueryBuilder(final Class<Entity> clazz) {
        final Table.Info tableInfo = clazz.getAnnotation(Table.Info.class);
        if (tableInfo == null) throw new IllegalStateException("Missing @Table on " + clazz);
        final String tableName = tableInfo.name();

        this.table = new Table<>(clazz, tableName);

        for (final Field field : clazz.getDeclaredFields()) {
            final Column.Info columnInfo = field.getAnnotation(Column.Info.class);
            if (columnInfo != null) {
                final String columnName = !columnInfo.name().isEmpty() ? columnInfo.name() : field.getName();
                final Column column = new Column(columnName, field, columnInfo);
                this.table.addColumn(column);
            }
        }
        this.table.immutable();
    }

    public Table<Entity> table() {
        return this.table;
    }

    public SelectEntity<Entity> selectAll() {
        return new SelectEntity<>(this.table);
    }

    public SelectEntity<Entity> selectWhere(final String whereClause, final Object... params) {
        return new SelectEntity<>(this.table, whereClause, params);
    }

    @SafeVarargs
    public final InsertEntity<Entity> insert(Entity... instances) {
        return new InsertEntity<Entity>(table, instances);
    }

    public UpdateEntity<Entity> update(Entity instance) {
        return new UpdateEntity<>(table, instance);
    }

    @SafeVarargs
    public final DeleteEntity<Entity> delete(Entity... instances) {
        return new DeleteEntity<>(table, instances);
    }


    public static <T> CompletableFuture<T> execute(final SqlConnection connection, final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return generate(query, bind, execute).execute(connection);
    }

    public static <T> Query<T> generate(final String query, final QueryBinder bind, final QueryExecutor<T> execute) {
        return new ParamQuery<>(query, bind, execute);
    }

    public CreateTable create(Dialect dialect, boolean ifNotExists) {
        return new CreateTable(this.table, dialect, ifNotExists);
    }

    public DropTable dropTable() {
        return new DropTable(this.table);
    }
}
