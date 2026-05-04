package dev.sweety.sql4j.api.connection.dialect;

import dev.sweety.sql4j.api.obj.ForeignKey;
import java.util.List;

public interface Dialect {

    String name();

    String sqlType(Class<?> javaType);

    String autoIncrement();

    String foreignKeyAction(ForeignKey.Action action);

    /**
     * Builds the dialect-specific UPSERT syntax.
     *
     * @param table      The table name
     * @param insertCols The columns to insert
     * @param updateCols The columns to update if a conflict occurs
     * @param pkCols     The primary key columns used to detect conflicts
     * @return The complete UPSERT SQL string
     */
    String upsertSyntax(String table, List<String> insertCols, List<String> updateCols, List<String> pkCols);

    /**
     * Builds the dialect-specific ALTER TABLE ADD COLUMN syntax.
     *
     * @param table     The table name
     * @param columnDef The complete column definition (e.g. "age INT NOT NULL")
     * @return The ALTER TABLE SQL string
     */
    default String addColumnSyntax(String table, String columnDef) {
        return "ALTER TABLE " + table + " ADD COLUMN " + columnDef;
    }

    default boolean supportsIfNotExists() {
        return true;
    }

    default boolean supportsGeneratedKeys() {
        return true;
    }

    default boolean inlinePrimaryKeyForAutoIncrement() {
        return false;
    }

    default boolean supportsForeignKeys() {
        return true;
    }
}

