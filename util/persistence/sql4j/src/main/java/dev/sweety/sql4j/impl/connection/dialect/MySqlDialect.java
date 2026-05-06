package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;
import java.util.List;
import java.util.stream.Collectors;

public class MySqlDialect implements Dialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String sqlType(Class<?> type) {
        if (type == int.class || type == Integer.class) return "INT";
        if (type == long.class || type == Long.class) return "BIGINT";
        if (type == boolean.class || type == Boolean.class) return "TINYINT(1)";
        if (type == float.class || type == Float.class) return "FLOAT";
        if (type == double.class || type == Double.class) return "DOUBLE";
        if (type == byte[].class) return "BLOB";
        if (type == java.util.UUID.class) return "VARCHAR(36)";
        if (type == java.math.BigDecimal.class) return "DECIMAL(19,4)";
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
            case RESTRICT -> "RESTRICT";
            case NO_ACTION -> "NO ACTION";
        };
    }

    @Override
    public String upsertSyntax(String table, List<String> insertCols, List<String> updateCols, List<String> pkCols) {
        String cols = insertCols.stream().map(this::escape).collect(Collectors.joining(", "));
        String placeholders = insertCols.stream().map(c -> "?").collect(Collectors.joining(", "));
        
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(escape(table)).append(" (").append(cols).append(") VALUES (").append(placeholders).append(")");
        
        if (updateCols.isEmpty()) {
            sb.append(" ON DUPLICATE KEY UPDATE ").append(escape(pkCols.get(0))).append(" = ").append(escape(pkCols.get(0)));
        } else {
            sb.append(" ON DUPLICATE KEY UPDATE ");
            String updates = updateCols.stream()
                    .map(c -> escape(c) + " = VALUES(" + escape(c) + ")")
                    .collect(Collectors.joining(", "));
            sb.append(updates);
        }
        
        return sb.toString();
    }

    @Override
    public String escape(String name) {
        return "`" + name + "`";
    }
}
