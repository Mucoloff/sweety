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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity-based SELECT query that returns fully populated (or partially populated) entity instances.
 */
public final class SelectEntity<T> extends AbstractQuery<List<T>> {

    private final Table<T> table;
    private final QueryCache cache;
    private final Dialect dialect;
    private final Object[] params;
    private final Criterion criterion;
    private final Metadata<T> metadata;

    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;
    private boolean includeDeleted = false;

    private record Metadata<T>(String sql, Constructor<T> constructor, List<Column<?>> selectedColumns) {}

    // --- Constructors ---

    public SelectEntity(Table<T> table, QueryCache cache, Dialect dialect) {
        this(table, (String) null, null, cache, dialect, (Object[]) null);
    }

    public SelectEntity(Table<T> table, String whereClause, QueryCache cache, Dialect dialect, Object... params) {
        this(table, whereClause, null, cache, dialect, params);
    }

    public SelectEntity(Table<T> table, String whereClause, Set<String> columnNames,
                        QueryCache cache, Dialect dialect, Object... params) {
        this(table, whereClause, null, columnNames, cache, dialect, params);
    }

    private SelectEntity(Table<T> table, String whereClause, Criterion criterion, Set<String> columnNames,
                        QueryCache cache, Dialect dialect, Object... params) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.params = params;
        this.criterion = criterion;

        String colKey = columnNames == null || columnNames.isEmpty() ? "*" : columnNames.stream().sorted().collect(Collectors.joining(","));
        String wherePart = whereClause != null ? whereClause : (criterion != null ? criterion.toSql() : "");
        String cacheKey = "select:meta:" + table.name() + ":" + wherePart + ":" + colKey;

        this.metadata = cache.getMetadata(cacheKey, _ -> buildMetadata(table, whereClause, criterion, columnNames));
    }

    private SelectEntity(Table<T> table, QueryCache cache, Metadata<T> metadata, Dialect dialect, Object[] params, Criterion criterion) {
        this.table = table;
        this.cache = cache;
        this.metadata = metadata;
        this.dialect = dialect;
        this.params = params;
        this.criterion = criterion;
    }

    public SelectEntity<T> copy(Object... params) {
        SelectEntity<T> copy = new SelectEntity<>(table, cache, metadata, dialect, params, criterion);
        copy.limit = this.limit;
        copy.offset = this.offset;
        copy.orderBy = this.orderBy;
        copy.ascending = this.ascending;
        copy.includeDeleted = this.includeDeleted;
        return copy;
    }

    public SelectEntity<T> where(Criterion criterion) {
        return new SelectEntity<>(table, null, criterion, null, cache, dialect);
    }

    public SelectEntity<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectEntity<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    public SelectEntity<T> orderBy(String column) {
        return orderBy(column, true);
    }

    public SelectEntity<T> orderByDescending(String column) {
        return orderBy(column, false);
    }

    public SelectEntity<T> orderBy(String column, boolean ascending) {
        this.orderBy = column;
        this.ascending = ascending;
        return this;
    }

    public SelectEntity<T> withDeleted() {
        this.includeDeleted = true;
        return this;
    }

    private Metadata<T> buildMetadata(Table<T> table, String whereClause, Criterion criterion, Set<String> columnNames) {
        List<Column<?>> selected = (columnNames == null || columnNames.isEmpty())
                ? table.columns()
                : columnNames.stream().map(table::column).collect(Collectors.toList());

        String cols = selected.stream().map(Column::name).collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder("SELECT ").append(cols).append(" FROM ").append(table.name());

        String wherePart = null;
        if (whereClause != null && !whereClause.isEmpty()) {
            wherePart = whereClause;
        } else if (criterion != null) {
            wherePart = criterion.toSql();
        }

        if (wherePart != null) {
            sb.append(" WHERE ").append(wherePart);
        }

        try {
            Constructor<T> ctor = table.clazz().getDeclaredConstructor();
            ctor.setAccessible(true);
            return new Metadata<>(sb.toString(), ctor, selected);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Entity " + table.clazz().getName() + " must have a no-arg constructor", e);
        }
    }

    @Override
    protected String buildSql() {
        String base = metadata.sql;
        Column softDeleteCol = table.softDeleteColumn();
        
        if (softDeleteCol != null && !includeDeleted) {
            String filter = softDeleteCol.name() + " = 0";
            if (base.toUpperCase(Locale.ENGLISH).contains(" WHERE ")) {
                base += " AND " + filter;
            } else {
                base += " WHERE " + filter;
            }
        }

        if (orderBy != null) {
            base += " ORDER BY " + orderBy + (ascending ? " ASC" : " DESC");
        }
        if (limit > 0) {
            base += " LIMIT " + limit;
            if (offset > 0) {
                base += " OFFSET " + offset;
            }
        }
        return base;
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
        try (ResultSet rs = ps.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                try {
                    T obj = metadata.constructor.newInstance();
                    for (Column c : metadata.selectedColumns) {
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
