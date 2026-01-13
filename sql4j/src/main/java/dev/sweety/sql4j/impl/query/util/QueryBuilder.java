package dev.sweety.sql4j.impl.query.util;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.impl.query.InsertQuery;
import dev.sweety.sql4j.impl.query.SelectQuery;

import java.lang.reflect.Field;


public class QueryBuilder<T> {

    private final Table<T> table;

    public QueryBuilder(final Class<T> clazz) {
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
    }

    public SelectQuery<T> selectWhere(final String whereClause, final Object... params) {
        return new SelectQuery<>(this.table, whereClause, params);
    }

    public SelectQuery<T> selectAll() {
        return new SelectQuery<>(this.table);
    }

    @SafeVarargs
    public final InsertQuery<T> insert(T... instances) {
        return new InsertQuery<T>(table, instances);
    }

}
