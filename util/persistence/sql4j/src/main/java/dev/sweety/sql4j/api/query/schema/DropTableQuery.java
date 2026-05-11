package dev.sweety.sql4j.api.query.schema;

/**
 * Marker interface for DDL queries that drop a table.
 *
 * <p>Obtain an instance via {@link dev.sweety.sql4j.api.repository.Repository#dropTable()}.
 *
 * <p>The concrete implementation ({@code dev.sweety.sql4j.impl.query.table.DropTable}) extends
 * {@code AbstractQuery<Void>} and can therefore be used directly as a
 * {@link dev.sweety.sql4j.api.query.Query Query&lt;Void&gt;}.
 */
public interface DropTableQuery {
}
