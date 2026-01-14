package dev.sweety.sql4j.impl.connection.sqlite;

import dev.sweety.sql4j.api.connection.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;

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

}
