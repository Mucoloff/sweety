package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.*;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.*;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


public class BaseRepository<Entity> implements Repository<Entity> {

    private final Table<Entity> table;
    private final Dialect dialect;
    private final QueryCache cache;
    private final TableRegistry registry;
    private final dev.sweety.sql4j.impl.cache.EntityCache entityCache;

    public BaseRepository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry, 
                      dev.sweety.sql4j.impl.cache.EntityCache entityCache) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.entityCache = entityCache;
    }

    public BaseRepository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry) {
        this(table, dialect, cache, registry, null);
    }

    /**
     * @deprecated Use {@link Database#createRepository(Class)} instead to ensure proper registry isolation.
     */
    @Deprecated
    public BaseRepository(final Class<Entity> entityClass) {
        this(new TableRegistry().get(entityClass), dev.sweety.sql4j.impl.connection.dialect.DialectType.SQLITE.dialect(), new QueryCache(), new TableRegistry(), null);
    }

    public Table<Entity> table() {
        return table;
    }

    // ─── Writes ────────────────────────────────────────────────────────────────

    public InsertQuery<Entity> insert(Entity entity) {
        InsertQuery<Entity> query = cache.getQuery("insertPrototype:" + table.name(),
                _ -> new InsertEntity<>(table, dialect, entity, cache)).copy(entity);
        if (entityCache == null || !entityCache.isCacheable(table.clazz())) return query;

        return new InsertQueryWrapper<>(query, () -> {
            Object pk = table.primaryKeys().get(0).get(entity);
            if (pk != null) entityCache.put(table.clazz(), pk, entity);
        });
    }

    public BatchQuery<Entity> insertBatch(Collection<Entity> entities) {
        return new InsertBatch<>(table, dialect, entities, cache);
    }

    public UpsertQuery<Entity> upsert(Entity entity) {
        return cache.getQuery("upsertPrototype:" + table.name(),
                _ -> new
                        UpsertEntity<>(table, dialect, entity, cache)).copy(entity);
    }

    public UpdateQuery<Entity> update(Entity entity) {
        UpdateQuery<Entity> query = cache.getQuery("updatePrototype:" + table.name(),
                _ -> new UpdateEntity<>(table, dialect, entity, cache)).copy(entity);
        if (entityCache == null || !entityCache.isCacheable(table.clazz())) return query;

        return new UpdateQueryWrapper<>(query, () -> {
            Object pk = table.primaryKeys().get(0).get(entity);
            if (pk != null) entityCache.put(table.clazz(), pk, entity);
        });
    }

    public BatchQuery<Entity> updateBatch(Collection<Entity> entities) {
        return new UpdateBatch<>(table, dialect, entities, cache);
    }

    @SuppressWarnings("unchecked")
    public DeleteQuery<Entity> delete(Entity entity) {
        Entity[] array = (Entity[]) java.lang.reflect.Array.newInstance(table.clazz(), 1);
        array[0] = entity;
        return delete(array);
    }

    @SafeVarargs
    public final DeleteQuery<Entity> delete(Entity... instances) {
        int count = instances != null ? instances.length : 0;
        DeleteQuery<Entity> query = cache.getQuery("deletePrototype:" + table.name() + ":" + count,
                _ -> new DeleteEntity<>(table, dialect, cache, instances)).copy(instances);
        
        if (entityCache == null || !entityCache.isCacheable(table.clazz())) return query;

        return new DeleteQueryWrapper<>(query, () -> {
            if (instances != null) {
                for (Entity e : instances) {
                    Object pk = table.primaryKeys().get(0).get(e);
                    if (pk != null) entityCache.evict(table.clazz(), pk);
                }
            }
        });
    }

    public ConditionalDeleteQuery<Entity> deleteWhere() {
        return new DeleteWhere<>(table, dialect, entityCache);
    }

    public dev.sweety.sql4j.api.query.PkContext<Entity> pk(Object... values) {
        return new dev.sweety.sql4j.api.query.PkContext<>(this, values);
    }

    public <T> Query<T> wrapWithCache(Object pkValue, java.util.function.Supplier<Query<T>> querySupplier) {
        if (entityCache != null && entityCache.isEnabled() && entityCache.isCacheable(table.clazz())) {
            Object pk = (pkValue instanceof Object[] arr && arr.length == 1) ? arr[0] : pkValue;
            T cached = (T) entityCache.get(table.clazz(), pk);
            if (cached != null) {
                return new AbstractQuery<T>() {
                    @Override protected String buildSql() { return "-- Cached lookup"; }
                    @Override public void bind(java.sql.PreparedStatement ps) {}
                    @Override public T execute(java.sql.PreparedStatement ps) { return cached; }
                    @Override public java.util.concurrent.CompletableFuture<T> execute(dev.sweety.sql4j.api.connection.SqlConnection con) {
                        return java.util.concurrent.CompletableFuture.completedFuture(cached);
                    }
                };
            }
        }
        
        Query<T> query = querySupplier.get();
        if (entityCache != null) {
            return query.extractObjects(res -> {
                if (res != null) {
                    Object pk = (pkValue instanceof Object[] arr && arr.length == 1) ? arr[0] : pkValue;
                    //noinspection unchecked
                    entityCache.put((Class<Object>) (Class<?>) table.clazz(), pk, res);
                }
                return res;
            });
        }
        return query;
    }

    public ConditionalDeleteQuery<Entity> deleteWhere(Criterion criterion) {
        return deleteWhere().where(criterion);
    }

    public ConditionalUpdateQuery<Entity> updateWhere() {
        return new UpdateWhere<>(table, dialect, entityCache);
    }

    public ConditionalUpdateQuery<Entity> updateWhere(Criterion criterion) {
        return updateWhere().where(criterion);
    }

    // ─── Relations ─────────────────────────────────────────────────────────────

    public Query<Integer> addRelation(Entity entity, Object related) {
        for (Table.Relation rel : table.relations()) {
            if (rel.type() == Table.Relation.Type.MANY_TO_MANY && rel.targetClass().isInstance(related)) {
                Table<?> junctionTable = registry.allTables().stream()
                        .filter(t -> t.name().equalsIgnoreCase(rel.joinTable()))
                        .findFirst()
                        .orElseThrow(() -> new Sql4jMappingException("Junction table " + rel.joinTable() + " not found"));

                Object id1 = table.primaryKeys().get(0).get(entity);
                Object id2 = registry.get(rel.targetClass()).primaryKeys().get(0).get(related);

                String sql = "INSERT INTO " + junctionTable.name() + " (" +
                             junctionTable.columns().get(0).name() + ", " +
                             junctionTable.columns().get(1).name() + ") VALUES (?, ?)";

                return Query.generic(sql, id1, id2).extractObjects(dev.sweety.sql4j.api.query.QueryResult::affectedRows);
            }
        }
        throw new Sql4jMappingException("No ManyToMany relation found between " + table.clazz().getSimpleName() + " and " + related.getClass().getSimpleName());
    }

    public Query<Integer> removeRelation(Entity entity, Object related) {
        for (Table.Relation rel : table.relations()) {
            if (rel.type() == Table.Relation.Type.MANY_TO_MANY && rel.targetClass().isInstance(related)) {
                Table<?> junctionTable = registry.allTables().stream()
                        .filter(t -> t.name().equalsIgnoreCase(rel.joinTable()))
                        .findFirst()
                        .orElseThrow(() -> new Sql4jMappingException("Junction table " + rel.joinTable() + " not found"));

                Object id1 = table.primaryKeys().get(0).get(entity);
                Object id2 = registry.get(rel.targetClass()).primaryKeys().get(0).get(related);

                String sql = "DELETE FROM " + junctionTable.name() + " WHERE " +
                             junctionTable.columns().get(0).name() + " = ? AND " +
                             junctionTable.columns().get(1).name() + " = ?";

                return Query.generic(sql, id1, id2).extractObjects(dev.sweety.sql4j.api.query.QueryResult::affectedRows);
            }
        }
        throw new Sql4jMappingException("No ManyToMany relation found between " + table.clazz().getSimpleName() + " and " + related.getClass().getSimpleName());
    }

    // ─── Entity-based reads ────────────────────────────────────────────────────

    /**
     * Selects all columns, no WHERE. Returns fully populated entity instances.
     */
    public SelectQuery<Entity> select() {
        SelectEntity<Entity> query = cache.getQuery("selectAllPrototype:" + table.name(),
                _ -> new SelectEntity<>(table, cache, dialect, registry));
        return query.withCache(entityCache); // We need to add withCache to SelectEntity
    }

    public SelectQuery<Entity> select(Criterion criterion) {
        return select().where(criterion);
    }


    /**
     * Selects all columns with a WHERE clause. Returns fully populated entity instances.
     *
     * <pre>{@code
     * repo.selectWhere("age > ?", 20).execute(connection).join();
     * }</pre>
     */
    public SelectQuery<Entity> selectWhere(String where, Object... params) {
        return new SelectEntity<>(table, where, cache, dialect, registry, params).withCache(entityCache);
    }

    public SelectQuery<Entity> select(Column<?>... columns) {
        return select().select(columns);
    }

    public SelectQuery<Entity> select(String... columnNames) {
        return new SelectEntity<Entity>(table, null, Set.of(columnNames), cache, dialect, registry, (Object[]) null).withCache(entityCache);
    }

    public SelectRawQuery selectRaw(Column<?>... columns) {
        return selectRawAll().select(columns);
    }

    public SelectRawQuery selectRaw(String... columnNames) {
        return new SelectRaw(table, null, Set.of(columnNames), cache, dialect, (Object[]) null);
    }

    /**
     * Selects only the specified columns with a WHERE clause.
     * Unspecified entity fields are left at zero/null.
     */
    public SelectQuery<Entity> selectWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectEntity<Entity>(table, where, columnNames, cache, dialect, registry, params).withCache(entityCache);
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
    public SelectRawQuery selectRawAll() {
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
    public SelectRawQuery selectRawWhere(String where, Object... params) {
        return new SelectRaw(table, where, null, cache, dialect, params);
    }

    public JoinBuilder joinBuilder() {
        return new dev.sweety.sql4j.impl.query.SelectJoin.Builder(registry).dialect(dialect).join(table);
    }

    /**
     * Selects specific columns with a WHERE clause and returns {@link List}<{@link Row}>.
     */
    public SelectRawQuery selectRawWhere(String where, Set<String> columnNames, Object... params) {
        return new SelectRaw(table, where, columnNames, cache, dialect, params);
    }

    // ─── DDL & Migration ───────────────────────────────────────────────────────

    public CreateTable create(boolean ifNotExists) {
        return new CreateTable(this.table, dialect, ifNotExists);
    }

    public Query<Void> createTable() {
        return create(true);
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
            throw new Sql4jQueryException("Failed to migrate schema for table " + table.name(), e);
        }
    }

    public Query<Void> dropTable() {
        return new DropTable(this.table);
    }
}
