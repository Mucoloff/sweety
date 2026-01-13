package dev.sweety.sql4j.impl.connection.mysql;

import dev.sweety.sql4j.api.connection.Dialect;

public class MySqlDialect implements Dialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String sqlType(Class<?> type) {
        if (type == int.class || type == Integer.class)
            return "INT";

        if (type == long.class || type == Long.class)
            return "BIGINT";

        if (type == boolean.class || type == Boolean.class)
            return "TINYINT(1)";

        if (type == float.class || type == Float.class)
            return "FLOAT";

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
}
