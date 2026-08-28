package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.math.pool.ObjectPool;
import dev.sweety.sql4j.impl.cache.EntityCache;
import dev.sweety.sql4j.impl.query.SelectJoin;

import dev.sweety.sql4j.api.interceptor.QueryInterceptor;
import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.query.Aggregate;
import dev.sweety.sql4j.api.query.Page;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.SelectQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import dev.sweety.sql4j.impl.query.util.ResultSetStream;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SelectEntity<T> extends AbstractQuery<List<T>> implements SelectQuery<T> {
    private EntityCache entityCache;

    private final Table<T> table;
    private final QueryCache cache;
    private final Dialect dialect;
    private final String whereClause;
    private final Object[] whereParams;

    private Criterion criterion;
    private Set<String> selectedColumnNames;
    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;
    private boolean includeDeleted = false;
    private List<Column<?>> groupByColumns;
    private Criterion havingCriterion;
    private List<Column<?>> projection;

    // Captured during buildSql
    private transient Metadata<T> activeMetadata;

    // O1: memoize the full SQL string — each SelectEntity instance is copy-on-write so
    // its SQL is stable after the first call to buildSql().
    private transient String cachedSql;

    // O5: shared pool of StringBuilder instances to avoid per-call allocation when
    // assembling the WHERE / GROUP BY / ORDER BY / LIMIT clauses.
    private static final ObjectPool<StringBuilder> SB_POOL = ObjectPool.shared(StringBuilder::new)
                    .reset(b -> b.setLength(0))
                    .maxSize(64)
                    .build();

    /**
     * Per-table metadata cached in {@link QueryCache}.
     *
     * @param sqlBase       SELECT … FROM … fragment (stable per column set + dialect)
     * @param constructor   MethodHandle for the entity no-arg constructor (faster than
     *                      {@code Constructor.newInstance()} after JIT warmup)
     * @param columns       ordered list of columns matching the SELECT projection
     * @param columnIndices 1-based JDBC ResultSet column indices matching {@code columns}
     *                      (O2: avoids per-row string → int lookup inside the JDBC driver)
     */
    private record Metadata<T>(String sqlBase, MethodHandle constructor,
                               List<Column<?>> columns, int[] columnIndices) {
    }

    private final TableRegistry registry;

    public SelectEntity(Table<T> table, QueryCache cache, Dialect dialect, TableRegistry registry) {
        this(table, null, null, cache, dialect, registry, (Object[]) null);
    }

    public SelectEntity(Table<T> table, String whereClause, QueryCache cache, Dialect dialect, TableRegistry registry, Object... params) {
        this(table, whereClause, null, cache, dialect, registry, params);
    }

    public SelectEntity(Table<T> table, String whereClause, Set<String> columnNames,
                        QueryCache cache, Dialect dialect, TableRegistry registry, Object... params) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.whereClause = whereClause;
        this.whereParams = params;
        this.selectedColumnNames = columnNames;
    }

    private SelectEntity(SelectEntity<T> other) {
        this.table = other.table;
        this.cache = other.cache;
        this.dialect = other.dialect;
        this.registry = other.registry;
        this.entityCache = other.entityCache;
        this.whereParams = other.whereParams;
        this.whereClause = other.whereClause;
        this.criterion = other.criterion;
        this.selectedColumnNames = other.selectedColumnNames;
        this.limit = other.limit;
        this.offset = other.offset;
        this.orderBy = other.orderBy;
        this.ascending = other.ascending;
        this.includeDeleted = other.includeDeleted;
        this.fetchRelations = other.fetchRelations;
        this.groupByColumns = other.groupByColumns;
        this.havingCriterion = other.havingCriterion;
        this.projection = other.projection;
        // cachedSql intentionally NOT copied: copy represents a potentially different query
    }

    public SelectEntity<T> copy() {
        return new SelectEntity<>(this);
    }

    public SelectEntity<T> where(Criterion criterion) {
        SelectEntity<T> copy = copy();
        copy.criterion = criterion;
        return copy;
    }

    public SelectEntity<T> select(Column<?>... columns) {
        SelectEntity<T> copy = copy();
        copy.projection = Arrays.asList(columns);
        copy.selectedColumnNames = Arrays.stream(columns).map(Column::name).collect(Collectors.toSet());
        return copy;
    }

    public SelectEntity<T> limit(int limit) {
        SelectEntity<T> copy = copy();
        copy.limit = limit;
        return copy;
    }

    public SelectEntity<T> offset(int offset) {
        SelectEntity<T> copy = copy();
        copy.offset = offset;
        return copy;
    }

    public SelectEntity<T> orderBy(String column, boolean ascending) {
        SelectEntity<T> copy = copy();
        copy.orderBy = column;
        copy.ascending = ascending;
        return copy;
    }

    public SelectEntity<T> orderBy(Column<?> column, boolean ascending) {
        return orderBy(column.name(), ascending);
    }

    public SelectEntity<T> withDeleted() {
        SelectEntity<T> copy = copy();
        copy.includeDeleted = true;
        return copy;
    }

    private Table.Relation[] fetchRelations;

    public SelectEntity<T> fetch(Table.Relation... relations) {
        SelectEntity<T> copy = copy();
        copy.fetchRelations = relations;
        return copy;
    }

    public SelectEntity<T> withCache(EntityCache cache) {
        this.entityCache = cache;
        return this;
    }

    @Override
    public SelectQuery<T> groupBy(Column<?>... columns) {
        SelectEntity<T> copy = copy();
        copy.groupByColumns = Arrays.asList(columns);
        return copy;
    }

    @Override
    public SelectQuery<T> having(Criterion criterion) {
        SelectEntity<T> copy = copy();
        copy.havingCriterion = criterion;
        return copy;
    }

    private transient SelectJoin delegatedJoin;
    private transient Query<List<T>> delegatedJoinQuery;

    private Query<List<T>> getDelegate() {
        if (delegatedJoinQuery == null && fetchRelations != null && fetchRelations.length > 0) {
            SelectJoin.Builder builder =
                    new SelectJoin.Builder(this.registry)
                            .dialect(dialect)
                            .includeDeleted(includeDeleted)
                            .join(table);

            for (Table.Relation rel : fetchRelations) {
                builder.join(rel);
            }
            if (whereClause != null) builder.where(whereClause, whereParams);
            if (criterion != null) builder.where(criterion);

            this.delegatedJoin = builder.build();
            if (limit > 0) delegatedJoin = delegatedJoin.limit(limit);
            if (offset > 0) delegatedJoin = delegatedJoin.offset(offset);
            if (orderBy != null) delegatedJoin = delegatedJoin.orderBy(orderBy, ascending);

            delegatedJoinQuery = delegatedJoin.mapToHierarchy(table.clazz());
        }
        return delegatedJoinQuery;
    }

    @Override
    protected String buildSql() {
        // O1: return memoized SQL when already built for this instance
        if (cachedSql != null) return cachedSql;

        Query<List<T>> delegate = getDelegate();
        if (delegate != null) return (cachedSql = delegate.sql());

        String colKey = selectedColumnNames == null || selectedColumnNames.isEmpty()
                ? "*"
                : selectedColumnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "select:base:" + table.name() + ":" + colKey + ":" + dialect.name();

        this.activeMetadata = cache.getMetadata(cacheKey, ignored -> {
            List<Column<?>> selected;
            if (projection != null && !projection.isEmpty()) {
                selected = projection;
            } else if (selectedColumnNames == null || selectedColumnNames.isEmpty()) {
                selected = table.columns();
            } else {
                selected = table.columns().stream()
                        .filter(c -> selectedColumnNames.contains(c.name()))
                        .collect(Collectors.toList());
            }

            String cols = selected.stream().map(c -> {
                if (c instanceof Aggregate.AggregateColumn ac) {
                    return ac.toSql(dialect) + " AS " + dialect.escape(ac.alias());
                }
                return c.toSql(dialect);
            }).collect(Collectors.joining(", "));
            String sqlBase = "SELECT " + cols + " FROM " + table.toSql(dialect);

            // O2: precompute 1-based ResultSet column indices (stable for this column list)
            int[] indices = new int[selected.size()];
            for (int i = 0; i < indices.length; i++) indices[i] = i + 1;

            // O3: MethodHandle for no-arg constructor — after JIT warmup equals a direct `new`
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        table.clazz(), MethodHandles.lookup());
                MethodHandle ctor = lookup.findConstructor(
                        table.clazz(), MethodType.methodType(void.class));
                return new Metadata<>(sqlBase, ctor, selected, indices);
            } catch (NoSuchMethodException e) {
                throw new Sql4jMappingException(
                        "Entity " + table.clazz().getName() + " must have a public no-arg constructor", e);
            } catch (IllegalAccessException e) {
                throw new Sql4jMappingException(
                        "Cannot access constructor of " + table.clazz().getName(), e);
            }
        });

        // O5: borrow a StringBuilder from the pool; release in finally to avoid leaks
        StringBuilder sqlBuilder = SB_POOL.acquire();
        try {
            sqlBuilder.append(activeMetadata.sqlBase);
            String where = buildWhereClause();
            if (!where.isEmpty()) {
                sqlBuilder.append(where);
            }

            if (groupByColumns != null && !groupByColumns.isEmpty()) {
                sqlBuilder.append(" GROUP BY ")
                        .append(groupByColumns.stream()
                                .map(c -> c.toSql(dialect))
                                .collect(Collectors.joining(", ")));
            }

            if (havingCriterion != null) {
                sqlBuilder.append(" HAVING ").append(havingCriterion.toSql(dialect));
            }

            if (orderBy != null) {
                sqlBuilder.append(" ORDER BY ")
                        .append(dialect.escape(orderBy))
                        .append(ascending ? " ASC" : " DESC");
            }

            if (limit > 0) {
                sqlBuilder.append(dialect.limitOffsetSyntax(limit, offset));
            }

            return (cachedSql = sqlBuilder.toString());
        } finally {
            SB_POOL.release(sqlBuilder);
        }
    }

    private String buildWhereClause() {
        List<String> wheres = new ArrayList<>();
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql(dialect));
        Column<?> softDeleteCol = table.softDeleteColumn();
        if (softDeleteCol != null && !includeDeleted) {
            wheres.add(softDeleteCol.toSql(dialect) + " = " + dialect.softDeleteFalse());
        }
        return wheres.isEmpty() ? "" : " WHERE " + String.join(" AND ", wheres);
    }

    @Override
    public CompletableFuture<Page<T>> executePage(
            SqlConnection con, int page, int size) {
        String countSql;
        getDelegate();
        if (delegatedJoin != null) {
            countSql = delegatedJoin.countSql();
        } else {
            countSql = "SELECT COUNT(*) FROM " + table.toSql(dialect) + buildWhereClause();
        }

        return con.executeAsync(Query.generate(countSql, this::bind, ps -> {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return 0L;
            }
        })).thenCompose(total -> this.limit(size).offset(page * size).execute(con)
                .thenApply(content -> {
                    int totalPages = (int) Math.ceil((double) total / size);
                    return new Page<>(content, total, totalPages, page, size);
                }));
    }

    @Override
    public CompletableFuture<List<Row>> executeAggregate(
            SqlConnection con) {
        return con.executeAsync(Query.generate(this.sql(), this::bind, ps -> {
            try (ResultSet rs = ps.executeQuery()) {
                return Row.fromResultSetAll(rs);
            }
        }));
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        Query<List<T>> delegate = getDelegate();
        if (delegate != null) {
            delegate.bind(ps);
            return;
        }

        int idx = 1;
        if (whereParams != null) {
            for (Object p : whereParams) ps.setObject(idx++, p);
        }
        if (criterion != null) {
            criterion.bind(ps, idx);
            idx += criterion.countParameters();
        }
        if (havingCriterion != null) {
            havingCriterion.bind(ps, idx);
        }
    }

    @Override
    public CompletableFuture<List<T>> execute(
            SqlConnection con) {
        if (entityCache != null && entityCache.isEnabled() && entityCache.isCacheable(table.clazz())) {
            boolean selectAll = selectedColumnNames == null || selectedColumnNames.isEmpty();
            boolean noJoin = fetchRelations == null || fetchRelations.length == 0;
            boolean noComplexWhere = whereClause == null || whereClause.isEmpty();

            if (selectAll && noJoin && noComplexWhere && criterion != null) {
                Object pkValue = criterion.getPkValue(table);
                if (pkValue != null) {
                    T cached = entityCache.get(table.clazz(), pkValue);
                    if (cached != null) {
                        return CompletableFuture.completedFuture(
                                Collections.singletonList(cached));
                    }
                }
            }
        }

        return super.execute(con).thenApply(list -> {
            if (entityCache != null && entityCache.isEnabled() && list != null && !list.isEmpty()) {
                boolean selectAll = selectedColumnNames == null || selectedColumnNames.isEmpty();
                if (selectAll) {
                    for (T entity : list) {
                        Object pk = table.primaryKeys().get(0).get(entity);
                        if (pk != null) entityCache.put(table.clazz(), pk, entity);
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<T> execute(PreparedStatement ps) throws SQLException {
        Query<List<T>> delegate = getDelegate();
        if (delegate != null) {
            return delegate.execute(ps);
        }

        if (activeMetadata == null) {
            sql();
        }

        try (ResultSet rs = ps.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        }
    }

    private T mapRow(ResultSet rs) throws SQLException {
        try {
            // O3: MethodHandle.invoke() — equivalent to direct `new` after JIT warmup
            @SuppressWarnings("unchecked")
            T obj = (T) activeMetadata.constructor().invoke();
            List<Column<?>> columns = activeMetadata.columns();
            int[] indices = activeMetadata.columnIndices();
            for (int i = 0; i < columns.size(); i++) {
                // O2: index-based lookup avoids string→int per-column scan in the JDBC driver
                columns.get(i).set(obj, rs.getObject(indices[i]));
            }
            return obj;
        } catch (Throwable e) {
            throw new SQLException("Failed to instantiate entity: " + table.clazz().getName(), e);
        }
    }

    @Override
    public CompletableFuture<Stream<T>> executeStream(
            SqlConnection con) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection jdbcCon = con.connection();
                String sql = this.sql();

                for (QueryInterceptor interceptor : con.interceptors()) {
                    interceptor.preExecute(this, jdbcCon);
                }

                PreparedStatement stmt = jdbcCon.prepareStatement(sql);
                this.bind(stmt);
                ResultSet rsStream = stmt.executeQuery();

                return ResultSetStream.create(jdbcCon, stmt, rsStream, this::mapRow);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, con.executor());
    }
}
