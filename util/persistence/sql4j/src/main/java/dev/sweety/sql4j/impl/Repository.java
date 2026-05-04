package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.SelectRaw;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Repository<Entity> {

    private final Table<Entity> table;
    private final Dialect dialect;
    private final QueryCache cache;

    public Repository(Table<Entity> table, Dialect dialect, QueryCache cache) {
        this.table = table;
        this.dialect = dialect;
        this.cache = cache;
    }

    /**
     * @deprecated Use {@link Database#createRepository(Class)} instead to ensure proper registry isolation.
     */
    @Deprecated
    public Repository(final Class<Entity> entityClass) {
        this(new TableRegistry().get(entityClass), new dev.sweety.sql4j.impl.connection.dialect.SqliteDialect(), new QueryCache());
    }

    public Table<Entity> table() {
        return table;
    }

    // ─── Writes ────────────────────────────────────────────────────────────────

    public InsertEntity<Entity> insert(Entity entity) {
        return cache.<InsertEntity<Entity>>getQuery("insertPrototype:" + table.name(),
                _ -> new InsertEntity<>(table, null, cache)).copy(entity);
    }

    public dev.sweety.sql4j.impl.query.entity.UpsertEntity<Entity> upsert(Entity entity) {
        return cache.<dev.sweety.sql4j.impl.query.entity.UpsertEntity<Entity>>getQuery("upsertPrototype:" + table.name(),
                _ -> new dev.sweety.sql4j.impl.query.entity.UpsertEntity<>(table, dialect, null, cache)).copy(entity);
    }

    public UpdateEntity<Entity> update(Entity entity) {
        return cache.<UpdateEntity<Entity>>getQuery("updatePrototype:" + table.name(),
                _ -> new UpdateEntity<>(table, null, cache)).copy(entity);
    }

    @SafeVarargs
    public final DeleteEntity<Entity> delete(Entity... instances) {
        int count = instances != null ? instances.length : 0;
        return cache.<DeleteEntity<Entity>>getQuery("deletePrototype:" + table.name() + ":" + count,
                _ -> new DeleteEntity<>(table, cache, instances)).copy(instances);
    }

    // ─── Entity-based reads ────────────────────────────────────────────────────

    /**
     * Selects all columns, no WHERE. Returns fully populated entity instances.
     */
    public SelectEntity<Entity> selectAll() {
        return cache.getQuery("selectAllPrototype:" + table.name(),
                _ -> new SelectEntity<>(table, cache));
    }

    /**
     * Selects all columns with a WHERE clause. Returns fully populated entity instances.
     *
     * <pre>{@code
     * repo.selectWhere("age > ?", 20).execute(connection).join();
     * }</pre>
     */
    public SelectEntity<Entity> selectWhere(String where, Object... params) {
        return new SelectEntity<>(table, where, cache, params);
    }

    /**
     * Selects only the specified columns. Unspecified entity fields are left at zero/null.
     *
     * <pre>{@code
     * repo.select("name", "age").execute(connection).join();
     * // → List<Entity> where getId() == 0 (not fetched)
     * }</pre>
     */
    public SelectEntity<Entity> select(String... columnNames) {
        Set<String> cols = Set.of(columnNames);
        return new SelectEntity<>(table, null, cols, cache, (Object[]) null);
    }

    /**
     * Selects only the specified columns with a WHERE clause.
     * Unspecified entity fields are left at zero/null.
     */
    public SelectEntity<Entity> selectWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectEntity<>(table, where, columnNames, cache, params);
    }

    // ─── Row-based reads (lightweight, no entity instantiation) ────────────────

    /**
     * Selects all columns and returns {@link List}<{@link Row}> — no entity instantiation.
     *
     * <pre>{@code
     * repo.selectRawAll().execute(connection).join()
     *     .forEach(row -> System.out.println(row.getString("name")));
     * }</pre>
     */
    public SelectRaw selectRawAll() {
        return cache.getQuery("selectRawAllPrototype:" + table.name(),
                _ -> new SelectRaw(table, cache));
    }

    /**
     * Selects specific columns and returns {@link List}<{@link Row}>.
     *
     * <pre>{@code
     * repo.selectRaw("name", "age").execute(connection).join();
     * // → [Row{name=Alice, age=25}, ...]
     * }</pre>
     */
    public SelectRaw selectRaw(String... columnNames) {
        Set<String> cols = Set.of(columnNames);
        return new SelectRaw(table, null, cols, cache, (Object[]) null);
    }

    /**
     * Selects specific columns with a WHERE clause and returns {@link List}<{@link Row}>.
     */
    public SelectRaw selectRawWhere(String where, Object... params) {
        return new SelectRaw(table, where, null, cache, params);
    }

    /**
     * Selects specific columns with a WHERE clause and returns {@link List}<{@link Row}>.
     */
    public SelectRaw selectRawWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectRaw(table, where, columnNames, cache, params);
    }

    // ─── DDL & Migration ───────────────────────────────────────────────────────

    public CreateTable create(boolean ifNotExists) {
        return new CreateTable(this.table, dialect, ifNotExists);
    }

    public void migrateSchema(dev.sweety.sql4j.api.connection.SqlConnection connection) {
        try (java.sql.Connection raw = connection.connection()) {
            java.sql.DatabaseMetaData metaData = raw.getMetaData();
            
            // In many databases, table names in metadata are stored in uppercase or lowercase.
            // We search case-insensitively by retrieving all and filtering, or by passing null.
            java.util.Set<String> existingColumns = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName.equalsIgnoreCase(table.name())) {
                        existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ENGLISH));
                    }
                }
            }

            for (Column c : table.columns()) {
                if (!existingColumns.contains(c.name().toLowerCase(java.util.Locale.ENGLISH))) {
                    String colDef = c.name() + " " + dialect.sqlType(c.field().getType());
                    // Skip NOT NULL for ADD COLUMN to avoid constraint errors on existing rows, 
                    // unless it's an advanced dialect/setup. For safety, we just add the column.
                    String sql = dialect.addColumnSyntax(table.name(), colDef);
                    
                    try (java.sql.Statement stmt = raw.createStatement()) {
                        stmt.execute(sql);
                    } catch (java.sql.SQLException e) {
                        // Log or ignore if the column couldn't be added (e.g. SQLite strictness)
                        System.err.println("[SQL4J] Failed to add column " + c.name() + " to " + table.name() + ": " + e.getMessage());
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to migrate schema for table " + table.name(), e);
        }
    }

    public DropTable dropTable() {
        return new DropTable(this.table);
    }
}
