package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;

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

        if (type == java.util.UUID.class)
            return "VARCHAR(36)";

        if (type == java.time.LocalDate.class)
            return "DATE";

        if (type == java.time.LocalDateTime.class)
            return "DATETIME";

        if (type == java.math.BigDecimal.class)
            return "DECIMAL(19,4)";

        if (type.isEnum())
            return "VARCHAR(255)";

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

    @Override
    public String upsertSyntax(String table, java.util.List<String> insertCols, java.util.List<String> updateCols, java.util.List<String> pkCols) {
        String cols = String.join(", ", insertCols);
        String placeholders = insertCols.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", "));
        
        if (updateCols.isEmpty()) {
            return "INSERT IGNORE INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")";
        }

        String updates = updateCols.stream()
                .map(c -> c + " = VALUES(" + c + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        
        return "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE " + updates;
    }
}
