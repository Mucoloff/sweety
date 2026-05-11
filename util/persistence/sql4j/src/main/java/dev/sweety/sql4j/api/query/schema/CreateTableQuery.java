package dev.sweety.sql4j.api.query.schema;

/**
 * Marker interface for DDL queries that create a table.
 *
 * <p>Obtain an instance via {@link dev.sweety.sql4j.api.repository.Repository#createTable()}.
 *
 * <p>The concrete implementation ({@code dev.sweety.sql4j.impl.query.table.CreateTable}) extends
 * {@code AbstractQuery<Void>} and can therefore be used directly as a
 * {@link dev.sweety.sql4j.api.query.Query Query&lt;Void&gt;}.
 */
public interface CreateTableQuery {
}
