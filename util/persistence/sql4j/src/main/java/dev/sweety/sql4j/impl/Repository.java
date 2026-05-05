package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.entity.DeleteWhere;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.SelectRaw;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.entity.UpsertEntity;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Repository<Entity> {

    private final Table<Entity> table;
    private final Dialect dialect;
    private final QueryCache cache;
    private final TableRegistry registry;

    public Repository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /**
     * @deprecated Use {@link Database#createRepository(Class)} instead to ensure proper registry isolation.
     */
    @Deprecated
    public Repository(final Class<Entity> entityClass) {
        this(new TableRegistry().get(entityClass), new dev.sweety.sql4j.impl.connection.dialect.SqliteDialect(), new QueryCache(), new TableRegistry());
    }

    public Table<Entity> table() {
        return table;
    }

    // ─── Writes ────────────────────────────────────────────────────────────────

    public InsertEntity<Entity> insert(Entity entity) {
        return cache.getQuery("insertPrototype:" + table.name(),
                _ -> new InsertEntity<>(table, entity, cache)).copy(entity);

    }

    public dev.sweety.sql4j.impl.query.entity.UpsertEntity<Entity> upsert(Entity entity) {
        return cache.getQuery("upsertPrototype:" + table.name(),
                _ -> new
                        UpsertEntity<>(table, dialect, entity, cache)).copy(entity);
    }

    public UpdateEntity<Entity> update(Entity entity) {
        return cache.getQuery("updatePrototype:" + table.name(),
                _ -> new UpdateEntity<>(table, entity, cache)).copy(entity);
    }

    @SafeVarargs
    public final DeleteEntity<Entity> delete(Entity... instances) {
        int count = instances != null ? instances.length : 0;
        return cache.getQuery("deletePrototype:" + table.name() + ":" + count,
                _ -> new DeleteEntity<>(table, cache, instances)).copy(instances);
    }

    public DeleteWhere<Entity> deleteWhere() {
        return new dev.sweety.sql4j.impl.query.entity.DeleteWhere<>(table);
    }

    public dev.sweety.sql4j.api.query.PkContext<Entity> pk(Object... values) {
        return new dev.sweety.sql4j.api.query.PkContext<>(this, values);
    }

    public DeleteWhere<Entity> deleteWhere(dev.sweety.sql4j.api.query.Criterion criterion) {
        return deleteWhere().where(criterion);
    }

    public dev.sweety.sql4j.impl.query.entity.UpdateWhere<Entity> updateWhere() {
        return new dev.sweety.sql4j.impl.query.entity.UpdateWhere<>(table);
    }

    public dev.sweety.sql4j.impl.query.entity.UpdateWhere<Entity> updateWhere(dev.sweety.sql4j.api.query.Criterion criterion) {
        return updateWhere().where(criterion);
    }

    // ─── Relations ─────────────────────────────────────────────────────────────

    public dev.sweety.sql4j.api.query.Query<Integer> addRelation(Entity entity, Object related) {
        for (Table.Relation rel : table.relations()) {
            if (rel.type() == Table.Relation.Type.MANY_TO_MANY && rel.targetClass().isInstance(related)) {
                Table<?> junctionTable = registry.allTables().stream()
                        .filter(t -> t.name().equalsIgnoreCase(rel.joinTable()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Junction table " + rel.joinTable() + " not found"));

                Object id1 = table.primaryKeys().get(0).get(entity);
                Object id2 = registry.get(rel.targetClass()).primaryKeys().get(0).get(related);

                String sql = "INSERT INTO " + junctionTable.name() + " (" +
                             junctionTable.columns().get(0).name() + ", " +
                             junctionTable.columns().get(1).name() + ") VALUES (?, ?)";

                return new dev.sweety.sql4j.api.query.AbstractQuery<Integer>() {
                    @Override protected String buildSql() { return sql; }
                    @Override public void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        ps.setObject(1, id1);
                        ps.setObject(2, id2);
                    }
                    @Override public Integer execute(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        return ps.executeUpdate();
                    }
                };
            }
        }
        throw new IllegalArgumentException("No ManyToMany relation found between " + table.clazz().getSimpleName() + " and " + related.getClass().getSimpleName());
    }

    public dev.sweety.sql4j.api.query.Query<Integer> removeRelation(Entity entity, Object related) {
        for (Table.Relation rel : table.relations()) {
            if (rel.type() == Table.Relation.Type.MANY_TO_MANY && rel.targetClass().isInstance(related)) {
                Table<?> junctionTable = registry.allTables().stream()
                        .filter(t -> t.name().equalsIgnoreCase(rel.joinTable()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Junction table " + rel.joinTable() + " not found"));

                Object id1 = table.primaryKeys().get(0).get(entity);
                Object id2 = registry.get(rel.targetClass()).primaryKeys().get(0).get(related);

                String sql = "DELETE FROM " + junctionTable.name() + " WHERE " +
                             junctionTable.columns().get(0).name() + " = ? AND " +
                             junctionTable.columns().get(1).name() + " = ?";

                return new dev.sweety.sql4j.api.query.AbstractQuery<Integer>() {
                    @Override protected String buildSql() { return sql; }
                    @Override public void bind(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        ps.setObject(1, id1);
                        ps.setObject(2, id2);
                    }
                    @Override public Integer execute(java.sql.PreparedStatement ps) throws java.sql.SQLException {
                        return ps.executeUpdate();
                    }
                };
            }
        }
        throw new IllegalArgumentException("No ManyToMany relation found between " + table.clazz().getSimpleName() + " and " + related.getClass().getSimpleName());
    }

    // ─── Entity-based reads ────────────────────────────────────────────────────

    /**
     * Selects all columns, no WHERE. Returns fully populated entity instances.
     */
    public SelectEntity<Entity> selectAll() {
        return cache.getQuery("selectAllPrototype:" + table.name(),
                _ -> new SelectEntity<>(table, cache, dialect));
    }

    public SelectEntity<Entity> select(dev.sweety.sql4j.api.query.Criterion criterion) {
        return selectAll().where(criterion);
    }


    /**
     * Selects all columns with a WHERE clause. Returns fully populated entity instances.
     *
     * <pre>{@code
     * repo.selectWhere("age > ?", 20).execute(connection).join();
     * }</pre>
     */
    public SelectEntity<Entity> selectWhere(String where, Object... params) {
        return new SelectEntity<>(table, where, cache, dialect, params);
    }

    public SelectEntity<Entity> select(Column<?>... columns) {
        return selectAll().select(columns);
    }

    public SelectEntity<Entity> select(String... columnNames) {
        return new SelectEntity<Entity>(table, null, Set.of(columnNames), cache, dialect, (Object[]) null);
    }

    public SelectRaw selectRaw(Column<?>... columns) {
        return selectRawAll().select(columns);
    }

    public SelectRaw selectRaw(String... columnNames) {
        return new SelectRaw(table, null, Set.of(columnNames), cache, dialect, (Object[]) null);
    }

    /**
     * Selects only the specified columns with a WHERE clause.
     * Unspecified entity fields are left at zero/null.
     */
    public SelectEntity<Entity> selectWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectEntity<Entity>(table, where, columnNames, cache, dialect, params);
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
                _ -> new SelectRaw(table, cache, dialect));
    }

    /**
     * Selects specific columns and returns {@link List}<{@link Row}>.
     *
     * <pre>{@code
     * repo.selectRaw("name", "age").execute(connection).join();
     * // → [Row{name=Alice, age=25}, ...]
     * }</pre>
     */
    /**
     * Selects specific columns with a WHERE clause and returns {@link List}<{@link Row}>.
     */
    public SelectRaw selectRawWhere(String where, Object... params) {
        return new SelectRaw(table, where, null, cache, dialect, params);
    }

    public dev.sweety.sql4j.impl.query.SelectJoin.Builder joinBuilder() {
        return new dev.sweety.sql4j.impl.query.SelectJoin.Builder(registry).dialect(dialect).join(table);
    }

    /**
     * Selects specific columns with a WHERE clause and returns {@link List}<{@link Row}>.
     */
    public SelectRaw selectRawWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectRaw(table, where, columnNames, cache, dialect, params);
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
                    String colDef = c.name() + " " + dialect.sqlType(c.type());
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
