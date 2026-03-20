package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;

public class H2Dialect implements Dialect {

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String sqlType(Class<?> type) {
        if (type == int.class || type == Integer.class)
            return "INT";

        if (type == long.class || type == Long.class)
            return "BIGINT";

        if (type == boolean.class || type == Boolean.class)
            return "BOOLEAN";

        if (type == float.class || type == Float.class)
            return "REAL";

        if (type == double.class || type == Double.class)
            return "DOUBLE";

        if (type == byte[].class)
            return "BLOB";

        return "VARCHAR(255)";
    }

    @Override
    public String autoIncrement() {
        return "AUTO_INCREMENT";
    }

    @Override
    public String foreignKeyAction(ForeignKey.Action action) {
        return switch (action) {
            case CASCADE -> "CASCADE";
            case SET_NULL -> "SET NULL";
            case RESTRICT, NO_ACTION -> "RESTRICT";
        };
    }
}

