package dev.sweety.sql4j.api.connection.dialect;

import dev.sweety.sql4j.api.obj.ForeignKey;
import java.util.List;

/**
 * Strategy interface that abstracts SQL syntax differences between database vendors.
 *
 * <p>Implementations are provided for H2, PostgreSQL, MySQL, MariaDB, and SQLite.
 * Obtain the active dialect from {@link dev.sweety.sql4j.api.connection.SqlConnection#dialect()}
 * or via {@code DialectType.XXXX.dialect()}.
 *
 * <p>Every method has a sensible default that is compatible with the SQL standard or the most
 * common vendor behaviour. Override only the methods that differ for your target database.
 */
public interface Dialect {

    /**
     * Returns the canonical dialect name (e.g. {@code "H2"}, {@code "POSTGRESQL"}).
     *
     * @return non-null, non-empty dialect identifier
     */
    String name();

    /**
     * Maps a Java type to its SQL column type declaration.
     *
     * @param javaType the Java class to map (e.g. {@code Integer.class}, {@code String.class})
     * @return the SQL type string (e.g. {@code "INTEGER"}, {@code "VARCHAR(255)"})
     */
    String sqlType(Class<?> javaType);

    /**
     * Returns the dialect-specific {@code AUTO_INCREMENT} or {@code GENERATED ALWAYS AS IDENTITY}
     * column modifier used in {@code CREATE TABLE} statements.
     *
     * @return the auto-increment DDL fragment
     */
    String autoIncrement();

    /**
     * Converts a foreign-key action enum to its SQL keyword.
     *
     * @param action the referential action (e.g. {@link ForeignKey.Action#CASCADE})
     * @return the SQL keyword (e.g. {@code "CASCADE"}, {@code "SET NULL"})
     */
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

    /**
     * Whether {@code CREATE TABLE IF NOT EXISTS} syntax is supported.
     * Defaults to {@code true} (supported by all major vendors).
     */
    default boolean supportsIfNotExists() {
        return true;
    }

    /**
     * Whether the JDBC driver returns generated keys after an INSERT.
     * Defaults to {@code true}; override to {@code false} for drivers that require
     * separate queries (e.g. some SQLite JDBC versions).
     */
    default boolean supportsGeneratedKeys() {
        return true;
    }

    /**
     * Whether the {@code PRIMARY KEY} constraint should be inlined on the auto-increment
     * column definition rather than added as a separate table constraint.
     * Defaults to {@code false} (separate constraint); SQLite overrides to {@code true}.
     */
    default boolean inlinePrimaryKeyForAutoIncrement() {
        return false;
    }

    /**
     * Whether this dialect supports {@code FOREIGN KEY} constraints in DDL.
     * Defaults to {@code true}; SQLite may override to {@code false}.
     */
    default boolean supportsForeignKeys() {
        return true;
    }

    /**
     * Builds the {@code LIMIT … OFFSET …} SQL fragment.
     *
     * @param limit  maximum rows ({@code < 0} means no limit)
     * @param offset rows to skip ({@code < 0} means no offset)
     * @return the SQL fragment, or an empty string if neither limit nor offset applies
     */
    default String limitOffsetSyntax(int limit, int offset) {
        if (limit < 0) return "";
        StringBuilder sb = new StringBuilder(" LIMIT ").append(limit);
        if (offset >= 0) sb.append(" OFFSET ").append(offset);
        return sb.toString();
    }

    /**
     * Escapes a database identifier (table or column name) to prevent conflicts with
     * reserved SQL keywords. Defaults to returning the name unquoted.
     *
     * @param name the raw identifier (e.g. {@code "order"})
     * @return the escaped identifier (e.g. {@code "\"order\""} for PostgreSQL)
     */
    default String escape(String name) {
        return name;
    }

    /**
     * Whether this dialect supports an UPSERT (INSERT-or-UPDATE) statement.
     * Defaults to {@code true}.
     */
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

