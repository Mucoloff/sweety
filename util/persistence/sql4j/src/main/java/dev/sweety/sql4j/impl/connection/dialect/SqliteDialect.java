package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;

import java.util.List;

public final class SqliteDialect implements Dialect {

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public String sqlType(Class<?> type) {
        if (type == int.class || type == Integer.class ||
                type == long.class || type == Long.class ||
                type == boolean.class || type == Boolean.class)
            return "INTEGER";

        if (type == float.class || type == Float.class ||
                type == double.class || type == Double.class)
            return "REAL";

        if (type == byte[].class)
            return "BLOB";

        if (type == java.util.UUID.class)
            return "TEXT";

        if (type == java.time.LocalDate.class || type == java.time.LocalDateTime.class)
            return "TEXT";

        if (type == java.math.BigDecimal.class)
            return "REAL";

        if (type.isEnum())
            return "TEXT";

        return "TEXT";
    }

    @Override
    public String autoIncrement() {
        return "AUTOINCREMENT";
    }

    @Override
    public boolean inlinePrimaryKeyForAutoIncrement() {
        return true;
    }


    @Override
    public String foreignKeyAction(ForeignKey.Action action) {
        return switch (action) {
            case CASCADE -> "CASCADE";
            case SET_NULL -> "SET NULL";
            case RESTRICT -> "RESTRICT";
            case NO_ACTION -> "NO ACTION";
        };
    }

    @Override
    public String upsertSyntax(String table, List<String> insertCols, List<String> updateCols, List<String> pkCols) {
        String cols = insertCols.stream().map(this::escape).collect(java.util.stream.Collectors.joining(", "));
        String placeholders = insertCols.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", "));
        String pks = pkCols.stream().map(this::escape).collect(java.util.stream.Collectors.joining(", "));
        
        if (updateCols.isEmpty()) {
            return "INSERT INTO " + escape(table) + " (" + cols + ") VALUES (" + placeholders + ") ON CONFLICT (" + pks + ") DO NOTHING";
        }

        String updates = updateCols.stream()
                .map(c -> escape(c) + " = excluded." + escape(c))
                .collect(java.util.stream.Collectors.joining(", "));
        
        return "INSERT INTO " + escape(table) + " (" + cols + ") VALUES (" + placeholders + ") ON CONFLICT (" + pks + ") DO UPDATE SET " + updates;
    }

    @Override
    public String escape(String name) {
        return "\"" + name + "\"";
    }

}
