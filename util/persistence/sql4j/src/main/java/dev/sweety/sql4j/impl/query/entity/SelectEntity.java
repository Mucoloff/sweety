package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SelectEntity<T> extends AbstractQuery<List<T>> {

    private final Table<T> table;
    private final QueryCache cache;
    private final Dialect dialect;
    private final Object[] params;
    private final String whereClause;
    
    private Criterion criterion;
    private Set<String> selectedColumnNames;
    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;
    private boolean includeDeleted = false;

    // Captured during buildSql
    private transient Metadata<T> activeMetadata;

    private record Metadata<T>(String sqlBase, Constructor<T> constructor, List<Column<?>> columns) {}

    public SelectEntity(Table<T> table, QueryCache cache, Dialect dialect) {
        this(table, null, null, cache, dialect, (Object[]) null);
    }

    public SelectEntity(Table<T> table, String whereClause, QueryCache cache, Dialect dialect, Object... params) {
        this(table, whereClause, null, cache, dialect, params);
    }

    public SelectEntity(Table<T> table, String whereClause, Set<String> columnNames,
                        QueryCache cache, Dialect dialect, Object... params) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.whereClause = whereClause;
        this.params = params;
        this.selectedColumnNames = columnNames;
    }

    private SelectEntity(SelectEntity<T> other) {
        this.table = other.table;
        this.cache = other.cache;
        this.dialect = other.dialect;
        this.params = other.params;
        this.whereClause = other.whereClause;
        this.criterion = other.criterion;
        this.selectedColumnNames = other.selectedColumnNames;
        this.limit = other.limit;
        this.offset = other.offset;
        this.orderBy = other.orderBy;
        this.ascending = other.ascending;
        this.includeDeleted = other.includeDeleted;
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

    @Override
    protected String buildSql() {
        String colKey = selectedColumnNames == null || selectedColumnNames.isEmpty() ? "*" : selectedColumnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "select:base:" + table.name() + ":" + colKey;

        this.activeMetadata = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> selected = (selectedColumnNames == null || selectedColumnNames.isEmpty())
                    ? table.columns()
                    : table.columns().stream().filter(c -> selectedColumnNames.contains(c.name())).collect(Collectors.toList());

            String cols = selected.stream().map(Column::name).collect(Collectors.joining(", "));
            String sql = "SELECT " + cols + " FROM " + table.name();
            
            try {
                Constructor<T> ctor = table.clazz().getDeclaredConstructor();
                ctor.setAccessible(true);
                return new Metadata<>(sql, ctor, selected);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Entity " + table.clazz().getName() + " must have a no-arg constructor", e);
            }
        });

        StringBuilder sql = new StringBuilder(activeMetadata.sqlBase);
        List<String> wheres = new ArrayList<>();
        
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql());
        
        Column<?> softDeleteCol = table.softDeleteColumn();
        if (softDeleteCol != null && !includeDeleted) {
            wheres.add(softDeleteCol.name() + " = 0");
        }

        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }

        if (orderBy != null) {
            sql.append(" ORDER BY ").append(orderBy).append(ascending ? " ASC" : " DESC");
        }

        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
            if (offset > 0) sql.append(" OFFSET ").append(offset);
        }

        return sql.toString();
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        int idx = 1;
        if (params != null) {
            for (Object p : params) ps.setObject(idx++, p);
        }
        if (criterion != null) {
            criterion.bind(ps, idx);
        }
    }

    @Override
    public List<T> execute(PreparedStatement ps) throws SQLException {
        if (activeMetadata == null) {
            // Fallback in case buildSql was somehow skipped or called on another thread without visibility
            // but AbstractQuery guarantees buildSql is called.
            sql(); 
        }

        try (ResultSet rs = ps.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                try {
                    T obj = activeMetadata.constructor.newInstance();
                    for (Column c : activeMetadata.columns) {
                        c.set(obj, rs.getObject(c.name()));
                    }
                    result.add(obj);
                } catch (Exception e) {
                    throw new SQLException("Failed to instantiate entity: " + table.clazz().getName(), e);
                }
            }
            return result;
        }
    }
}
