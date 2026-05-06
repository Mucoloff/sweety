package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.SelectQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SelectEntity<T> extends AbstractQuery<List<T>> implements SelectQuery<T> {
    private dev.sweety.sql4j.impl.cache.EntityCache entityCache;

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

    private record Metadata<T>(String sqlBase, Constructor<T> constructor, List<Column<?>> columns) {}

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
        copy.projection = java.util.Arrays.asList(columns);
        copy.selectedColumnNames = java.util.Arrays.stream(columns).map(Column::name).collect(Collectors.toSet());
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

    public SelectEntity<T> withCache(dev.sweety.sql4j.impl.cache.EntityCache cache) {
        this.entityCache = cache;
        return this;
    }

    @Override
    public SelectQuery<T> groupBy(Column<?>... columns) {
        SelectEntity<T> copy = copy();
        copy.groupByColumns = java.util.Arrays.asList(columns);
        return copy;
    }

    @Override
    public SelectQuery<T> having(Criterion criterion) {
        SelectEntity<T> copy = copy();
        copy.havingCriterion = criterion;
        return copy;
    }

    private transient dev.sweety.sql4j.impl.query.SelectJoin delegatedJoin;
    private transient dev.sweety.sql4j.api.query.Query<List<T>> delegatedJoinQuery;

    private dev.sweety.sql4j.api.query.Query<List<T>> getDelegate() {
        if (delegatedJoinQuery == null && fetchRelations != null && fetchRelations.length > 0) {
            dev.sweety.sql4j.impl.query.SelectJoin.Builder builder = 
                new dev.sweety.sql4j.impl.query.SelectJoin.Builder(this.registry)
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
        dev.sweety.sql4j.api.query.Query<List<T>> delegate = getDelegate();
        if (delegate != null) return delegate.sql();

        dev.sweety.sql4j.api.connection.dialect.Dialect dialect = this.dialect;
        String colKey = selectedColumnNames == null || selectedColumnNames.isEmpty() ? "*" : selectedColumnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "select:base:" + table.name() + ":" + colKey + ":" + dialect.name();

        this.activeMetadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> selected;
            if (projection != null && !projection.isEmpty()) {
                selected = projection;
            } else if (selectedColumnNames == null || selectedColumnNames.isEmpty()) {
                selected = table.columns();
            } else {
                selected = table.columns().stream().filter(c -> selectedColumnNames.contains(c.name())).collect(Collectors.toList());
            }

            String cols = selected.stream().map(c -> {
                if (c instanceof dev.sweety.sql4j.api.query.Aggregate.AggregateColumn ac) {
                    return ac.toSql(dialect) + " AS " + dialect.escape(ac.alias());
                }
                return c.toSql(dialect);
            }).collect(Collectors.joining(", "));
            String sqlBase = "SELECT " + cols + " FROM " + table.toSql(dialect);

            try {
                Constructor<T> ctor = table.clazz().getDeclaredConstructor();
                ctor.setAccessible(true);
                return new Metadata<>(sqlBase, ctor, selected);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Entity " + table.clazz().getName() + " must have a no-arg constructor", e);
            }
        });

        StringBuilder sqlBuilder = new StringBuilder(activeMetadata.sqlBase);
        String where = buildWhereClause();
        if (!where.isEmpty()) {
            sqlBuilder.append(where);
        }

        if (groupByColumns != null && !groupByColumns.isEmpty()) {
            sqlBuilder.append(" GROUP BY ").append(groupByColumns.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(", ")));
        }

        if (havingCriterion != null) {
            sqlBuilder.append(" HAVING ").append(havingCriterion.toSql(dialect));
        }

        if (orderBy != null) {
            sqlBuilder.append(" ORDER BY ").append(dialect.escape(orderBy)).append(ascending ? " ASC" : " DESC");
        }

        if (limit > 0) {
            sqlBuilder.append(dialect.limitOffsetSyntax(limit, offset));
        }

        return sqlBuilder.toString();
    }

    private String buildWhereClause() {
        List<String> wheres = new ArrayList<>();
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql(dialect));
        Column<?> softDeleteCol = table.softDeleteColumn();
        if (softDeleteCol != null && !includeDeleted) {
            wheres.add(softDeleteCol.toSql(dialect) + " = 0");
        }
        return wheres.isEmpty() ? "" : " WHERE " + String.join(" AND ", wheres);
    }

    @Override
    public java.util.concurrent.CompletableFuture<dev.sweety.sql4j.api.query.Page<T>> executePage(dev.sweety.sql4j.api.connection.SqlConnection con, int page, int size) {
        String countSql;
        getDelegate(); // ensures delegatedJoin is built if needed
        if (delegatedJoin != null) {
            countSql = delegatedJoin.countSql();
        } else {
            countSql = "SELECT COUNT(*) FROM " + table.toSql(dialect) + buildWhereClause();
        }

        return con.executeAsync(dev.sweety.sql4j.api.query.Query.generate(countSql, this::bind, ps -> {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return 0L;
            }
        })).thenCompose(total -> this.limit(size).offset(page * size).execute(con).thenApply(content -> {
            int totalPages = (int) Math.ceil((double) total / size);
            return new dev.sweety.sql4j.api.query.Page<>(content, total, totalPages, page, size);
        }));
    }

    @Override
    public java.util.concurrent.CompletableFuture<List<dev.sweety.sql4j.api.obj.Row>> executeAggregate(dev.sweety.sql4j.api.connection.SqlConnection con) {
        return con.executeAsync(dev.sweety.sql4j.api.query.Query.generate(this.sql(), this::bind, ps -> {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return dev.sweety.sql4j.api.obj.Row.fromResultSetAll(rs);
            }
        }));
    }


    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        dev.sweety.sql4j.api.query.Query<List<T>> delegate = getDelegate();
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
    public java.util.concurrent.CompletableFuture<List<T>> execute(dev.sweety.sql4j.api.connection.SqlConnection con) {
        if (entityCache != null && entityCache.isEnabled()) {
            // Check if it's a simple "select * from table where id = ?"
            boolean selectAll = selectedColumnNames == null || selectedColumnNames.isEmpty();
            boolean noJoin = fetchRelations == null || fetchRelations.length == 0;
            boolean noComplexWhere = whereClause == null || whereClause.isEmpty();
            
            if (selectAll && noJoin && noComplexWhere && criterion != null) {
                Object pkValue = criterion.getPkValue(table);
                if (pkValue != null) {
                    T cached = entityCache.get(table.clazz(), pkValue);
                    if (cached != null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(java.util.Collections.singletonList(cached));
                    }
                }
            }
        }

        return super.execute(con).thenApply(list -> {
            if (entityCache != null && entityCache.isEnabled() && list != null && !list.isEmpty()) {
                // If it was a by-id query, we might want to cache it now if not already cached.
                // But generally Repository.wrapWithCache or execute handles this.
                // Here we just ensure that if we got results, and it's a @Cacheable entity, we could cache them.
                // However, we only cache if it's a full select.
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
        dev.sweety.sql4j.api.query.Query<List<T>> delegate = getDelegate();
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
            T obj = activeMetadata.constructor.newInstance();
            for (dev.sweety.sql4j.api.obj.Column c : activeMetadata.columns) {
                c.set(obj, rs.getObject(c.name()));
            }
            return obj;
        } catch (Exception e) {
            throw new SQLException("Failed to instantiate entity: " + table.clazz().getName(), e);
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.stream.Stream<T>> executeStream(dev.sweety.sql4j.api.connection.SqlConnection con) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                java.sql.Connection jdbcCon = con.connection();
                String sql = this.sql();
                
                // Interceptors preExecute
                for (dev.sweety.sql4j.api.interceptor.QueryInterceptor interceptor : con.interceptors()) {
                    interceptor.preExecute(this, jdbcCon);
                }

                java.sql.PreparedStatement ps = jdbcCon.prepareStatement(sql);
                this.bind(ps);
                java.sql.ResultSet rs = ps.executeQuery();
                
                return dev.sweety.sql4j.impl.query.util.ResultSetStream.create(jdbcCon, ps, rs, this::mapRow);
            } catch (java.sql.SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, con.executor());
    }

}
