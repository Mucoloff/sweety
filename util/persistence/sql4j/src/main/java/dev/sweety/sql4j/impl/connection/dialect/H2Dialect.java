package dev.sweety.sql4j.impl.connection.dialect;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.ForeignKey;

import java.util.List;

class H2Dialect implements Dialect {

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

        if (type == java.util.UUID.class)
            return "UUID";

        if (type == java.time.LocalDate.class)
            return "DATE";

        if (type == java.time.LocalDateTime.class)
            return "TIMESTAMP";

        if (type == java.math.BigDecimal.class)
            return "DECIMAL";

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
    public String upsertSyntax(String table, List<String> insertCols, List<String> updateCols, List<String> pkCols) {
        // H2 MERGE INTO requires KEY columns to be present in the column list.
        // UpsertEntity excludes auto-increment PKs from insertCols, so we must re-add
        // them here. When the PK is null at runtime, UpsertEntity falls back to a plain
        // INSERT (see UpsertEntity.buildSql); when it is set, the full column list works.
        List<String> allCols = new java.util.ArrayList<>(pkCols);
        allCols.addAll(insertCols);
        String cols = String.join(", ", allCols);
        String placeholders = allCols.stream().map(c -> "?").collect(java.util.stream.Collectors.joining(", "));
        String pks = String.join(", ", pkCols);

        return "MERGE INTO " + table + " (" + cols + ") KEY (" + pks + ") VALUES (" + placeholders + ")";
    }

    // H2 uses a proper SQL BOOLEAN type; integer literals 0/1 are not comparable.
    @Override
    public String softDeleteFalse() { return "FALSE"; }

    @Override
    public String softDeleteTrue() { return "TRUE"; }
}

