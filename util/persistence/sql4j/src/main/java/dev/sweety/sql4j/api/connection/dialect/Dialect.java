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

    default String limitOffsetSyntax(int limit, int offset) {
        if (limit < 0) return "";
        StringBuilder sb = new StringBuilder(" LIMIT ").append(limit);
        if (offset >= 0) sb.append(" OFFSET ").append(offset);
        return sb.toString();
    }

    /**
     * Escapes a database identifier (table or column name).
     */
    default String escape(String name) {
        return name; // Default no escape
    }

    default boolean supportsUpsert() {
        return true;
    }

    // ─── Bug fix (Phase B): soft-delete boolean literals ─────────────────────────
    // SQLite / MySQL / MariaDB store BOOLEAN as INTEGER, so "= 0" / "= 1" is correct.
    // H2 and PostgreSQL use a proper BOOLEAN type and reject integer comparisons.
    // Subclasses that use real BOOLEAN columns must override these two methods.

    /**
     * SQL literal representing {@code false} for the soft-delete filter.
     * Defaults to {@code "0"} (compatible with SQLite, MySQL, MariaDB).
     */
    default String softDeleteFalse() {
        return "0";
    }

    /**
     * SQL literal representing {@code true} for the soft-delete update.
     * Defaults to {@code "1"} (compatible with SQLite, MySQL, MariaDB).
     */
    default String softDeleteTrue() {
        return "1";
    }
}

