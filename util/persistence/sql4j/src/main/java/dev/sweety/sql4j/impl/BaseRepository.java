package dev.sweety.sql4j.impl;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.annotation.FetchType;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.api.query.ConditionalDeleteQuery;
import dev.sweety.sql4j.api.query.ConditionalUpdateQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.DeleteQuery;
import dev.sweety.sql4j.api.query.InsertQuery;
import dev.sweety.sql4j.api.query.JoinBuilder;
import dev.sweety.sql4j.api.query.PkContext;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.QueryResult;
import dev.sweety.sql4j.api.query.SelectQuery;
import dev.sweety.sql4j.api.query.SelectRawQuery;
import dev.sweety.sql4j.api.query.UpdateQuery;
import dev.sweety.sql4j.api.query.UpsertQuery;
import dev.sweety.sql4j.api.repository.Repository;
import dev.sweety.sql4j.impl.query.SelectJoin;
import dev.sweety.sql4j.impl.query.QueryCache;
import dev.sweety.sql4j.impl.query.entity.DeleteEntity;
import dev.sweety.sql4j.impl.query.entity.DeleteWhere;
import dev.sweety.sql4j.impl.query.entity.InsertBatch;
import dev.sweety.sql4j.impl.query.entity.InsertEntity;
import dev.sweety.sql4j.impl.query.entity.SelectEntity;
import dev.sweety.sql4j.impl.query.entity.SelectRaw;
import dev.sweety.sql4j.impl.query.entity.UpdateBatch;
import dev.sweety.sql4j.impl.query.entity.UpdateEntity;
import dev.sweety.sql4j.impl.query.entity.UpdateWhere;
import dev.sweety.sql4j.impl.query.entity.UpsertEntity;
import dev.sweety.sql4j.impl.cache.EntityCache;
import dev.sweety.sql4j.impl.connection.dialect.DialectType;
import dev.sweety.sql4j.impl.query.table.CreateTable;
import dev.sweety.sql4j.impl.query.table.DropTable;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;


public class BaseRepository<Entity> implements Repository<Entity> {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BaseRepository.class);

    private final Table<Entity> table;
    private final Dialect dialect;
    private final QueryCache cache;
    private final TableRegistry registry;
    /** Accessible by generated {@code *RepositoryImpl} subclasses for {@code @CacheEvict}. */
    protected final EntityCache entityCache;
    private final int batchChunkSize;

    public BaseRepository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry,
                          EntityCache entityCache) {
        this(table, dialect, cache, registry, entityCache, 0);
    }

    public BaseRepository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry,
                          EntityCache entityCache, int batchChunkSize) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.entityCache = entityCache;
        this.batchChunkSize = batchChunkSize;
    }

    public BaseRepository(Table<Entity> table, Dialect dialect, QueryCache cache, TableRegistry registry) {
        this(table, dialect, cache, registry, null);
    }

    /**
     * @deprecated Use {@link Database#createRepository(Class)} instead to ensure proper registry isolation.
     */
    @Deprecated
    public BaseRepository(final Class<Entity> entityClass) {
        this(new TableRegistry().get(entityClass), DialectType.SQLITE.dialect(), new QueryCache(), new TableRegistry(), null);
    }

    public @NotNull Table<Entity> table() {
        return table;
    }

    // ─── Writes ────────────────────────────────────────────────────────────────

    public @NotNull InsertQuery<Entity> insert(@NotNull Entity entity) {
        InsertQuery<Entity> query = cache.getQuery("insertPrototype:" + table.name(),
                ignored -> new InsertEntity<>(table, dialect, entity, cache)).copy(entity);
        if (entityCache == null || !entityCache.isCacheable(table.clazz())) return query;

        return new InsertQueryWrapper<>(query, () -> {
            Object pk = table.primaryKeys().get(0).get(entity);
            if (pk != null) entityCache.put(table.clazz(), pk, entity);
        });
    }

    public @NotNull BatchQuery<Entity> insertBatch(@NotNull Collection<Entity> entities) {
        return new InsertBatch<>(table, dialect, entities, cache, batchChunkSize);
    }

    public @NotNull UpsertQuery<Entity> upsert(@NotNull Entity entity) {
        return cache.getQuery("upsertPrototype:" + table.name(),
                ignored -> new
                        UpsertEntity<>(table, dialect, entity, cache)).copy(entity);
    }

    public @NotNull UpdateQuery<Entity> update(@NotNull Entity entity) {
        UpdateQuery<Entity> query = cache.getQuery("updatePrototype:" + table.name(),
                ignored -> new UpdateEntity<>(table, dialect, entity, cache)).copy(entity);
        if (entityCache == null || !entityCache.isCacheable(table.clazz())) return query;

        return new UpdateQueryWrapper<>(query, () -> {
            Object pk = table.primaryKeys().get(0).get(entity);
            if (pk != null) entityCache.put(table.clazz(), pk, entity);
        });
    }

    public @NotNull BatchQuery<Entity> updateBatch(@NotNull Collection<Entity> entities) {
        return new UpdateBatch<>(table, dialect, entities, cache, batchChunkSize);
    }

    @SuppressWarnings("unchecked")
    public @NotNull DeleteQuery<Entity> delete(@NotNull Entity entity) {
        Entity[] array = (Entity[]) Array.newInstance(table.clazz(), 1);
        array[0] = entity;
        return delete(array);
    }

    @SafeVarargs
    public final DeleteQuery<Entity> delete(Entity... instances) {
        int count = instances != null ? instances.length : 0;
        DeleteQuery<Entity> query = cache.getQuery("deletePrototype:" + table.name() + ":" + count,
                ignored -> new DeleteEntity<>(table, dialect, cache, instances)).copy(instances);
        
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

    public @NotNull ConditionalDeleteQuery<Entity> deleteWhere() {
        return new DeleteWhere<>(table, dialect, entityCache);
    }

    public PkContext<Entity> pk(Object... values) {
        return new PkContext<>(this, values);
    }

    public <T> Query<T> wrapWithCache(Object pkValue, Supplier<Query<T>> querySupplier) {
        if (entityCache != null && entityCache.isEnabled() && entityCache.isCacheable(table.clazz())) {
            Object pk = (pkValue instanceof Object[] arr && arr.length == 1) ? arr[0] : pkValue;
            T cached = (T) entityCache.get(table.clazz(), pk);
            if (cached != null) {
                return new AbstractQuery<T>() {
                    @Override protected String buildSql() { return "-- Cached lookup"; }
                    @Override public void bind(PreparedStatement ps) {}
                    @Override public T execute(PreparedStatement ps) { return cached; }
                    @Override public CompletableFuture<T> execute(SqlConnection con) {
                        return CompletableFuture.completedFuture(cached);
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

    public @NotNull ConditionalDeleteQuery<Entity> deleteWhere(@NotNull Criterion criterion) {
        return deleteWhere().where(criterion);
    }

    public @NotNull ConditionalUpdateQuery<Entity> updateWhere() {
        return new UpdateWhere<>(table, dialect, entityCache);
    }

    public @NotNull ConditionalUpdateQuery<Entity> updateWhere(@NotNull Criterion criterion) {
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

                return Query.generic(sql, id1, id2).extractObjects(QueryResult::affectedRows);
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

                return Query.generic(sql, id1, id2).extractObjects(QueryResult::affectedRows);
            }
        }
        throw new Sql4jMappingException("No ManyToMany relation found between " + table.clazz().getSimpleName() + " and " + related.getClass().getSimpleName());
    }

    /**
     * Applies {@link FetchType#EAGER} {@link Table.Relation}s (collections only) so plain
     * {@code select()} matches {@link SelectQuery#fetch(Table.Relation[])} for those relations.
     */
    private SelectEntity<Entity> withEagerRelations(SelectEntity<Entity> base) {
        List<Table.Relation> eager = table.relations().stream()
                .filter(r -> r.fetchType() == FetchType.EAGER
                        && (r.type() == Table.Relation.Type.ONE_TO_MANY
                            || r.type() == Table.Relation.Type.MANY_TO_MANY))
                .toList();
        if (eager.isEmpty()) {
            return base;
        }
        return base.fetch(eager.toArray(Table.Relation[]::new));
    }

    // ─── Entity-based reads ────────────────────────────────────────────────────

    /**
     * Selects all columns, no WHERE. Returns fully populated entity instances.
     */
    public SelectQuery<Entity> select() {
        SelectEntity<Entity> query = cache.getQuery("selectAllPrototype:" + table.name(),
                ignored -> withEagerRelations(new SelectEntity<>(table, cache, dialect, registry)));
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
        return withEagerRelations(new SelectEntity<>(table, where, cache, dialect, registry, params)).withCache(entityCache);
    }

    public SelectQuery<Entity> select(Column<?>... columns) {
        return select().select(columns);
    }

    public SelectQuery<Entity> select(String... columnNames) {
        return withEagerRelations(
                new SelectEntity<Entity>(table, null, Set.of(columnNames), cache, dialect, registry, (Object[]) null))
                .withCache(entityCache);
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
        return withEagerRelations(new SelectEntity<Entity>(table, where, columnNames, cache, dialect, registry, params))
                .withCache(entityCache);
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
                ignored -> new SelectRaw(table, cache, dialect));
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
        return new SelectJoin.Builder(registry).dialect(dialect).join(table);
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

    public @NotNull Query<Void> createTable() {
        return create(true);
    }

    public void migrateSchema(SqlConnection connection) {
        try (Connection raw = connection.connection()) {
            DatabaseMetaData metaData = raw.getMetaData();
            
            // In many databases, table names in metadata are stored in uppercase or lowercase.
            // We search case-insensitively by retrieving all and filtering, or by passing null.
            Set<String> existingColumns = new HashSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName.equalsIgnoreCase(table.name())) {
                        existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH));
                    }
                }
            }

            for (Column<?> c : table.columns()) {
                if (!existingColumns.contains(c.name().toLowerCase(Locale.ENGLISH))) {
                    String colDef = c.name() + " " + dialect.sqlType(c.type());
                    // DEFAULT must be applied here too (not just in CreateTable) — without it, existing
                    // rows get NULL for the new column, which a primitive-typed field (e.g. `boolean`,
                    // not `Boolean`) can't represent, silently coercing/NPEing on read. Most dialects
                    // backfill existing rows with DEFAULT on ADD COLUMN, so this alone fixes it.
                    // Still skip NOT NULL for ADD COLUMN to avoid constraint errors on existing rows
                    // in dialects that don't backfill before enforcing it.
                    if (c.defaultValue() != null && !c.defaultValue().isEmpty()) {
                        colDef += " DEFAULT " + c.defaultValue();
                    }
                    String sql = dialect.addColumnSyntax(table.name(), colDef);
                    
                    try (Statement stmt = raw.createStatement()) {
                        stmt.execute(sql);
                    } catch (SQLException e) {
                        // Log-and-continue: some dialects (e.g. SQLite strictness) reject certain
                        // ADD COLUMN forms — the migration proceeds without this one column.
                        LOGGER.warn("[SQL4J] Failed to add column {} to {}: {}", c.name(), table.name(), e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            throw new Sql4jQueryException("Failed to migrate schema for table " + table.name(), e);
        }
    }

    public @NotNull Query<Void> dropTable() {
        return new DropTable(this.table);
    }
}
