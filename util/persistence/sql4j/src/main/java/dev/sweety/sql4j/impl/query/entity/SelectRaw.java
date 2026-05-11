package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.sweety.sql4j.api.query.SelectRawQuery;

public final class SelectRaw extends AbstractQuery<List<Row>> implements SelectRawQuery {

    private final Table<?> table;
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

    private record Metadata(String sqlBase, List<String> columnNames) {}

    public SelectRaw(Table<?> table, QueryCache cache, Dialect dialect) {
        this(table, null, null, cache, dialect, (Object[]) null);
    }

    public SelectRaw(Table<?> table, String whereClause, Set<String> columnNames,
                     QueryCache cache, Dialect dialect, Object... params) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.whereClause = whereClause;
        this.params = params;
        this.selectedColumnNames = columnNames;
    }

    private SelectRaw(SelectRaw other) {
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

    public SelectRaw copy() {
        return new SelectRaw(this);
    }

    public SelectRaw where(Criterion criterion) {
        SelectRaw copy = copy();
        copy.criterion = criterion;
        return copy;
    }

    public SelectRaw select(Column<?>... columns) {
        SelectRaw copy = copy();
        copy.selectedColumnNames = java.util.Arrays.stream(columns).map(Column::name).collect(Collectors.toSet());
        return copy;
    }

    public SelectRaw limit(int limit) {
        SelectRaw copy = copy();
        copy.limit = limit;
        return copy;
    }

    public SelectRaw offset(int offset) {
        SelectRaw copy = copy();
        copy.offset = offset;
        return copy;
    }

    public SelectRaw orderBy(String column, boolean ascending) {
        SelectRaw copy = copy();
        copy.orderBy = column;
        copy.ascending = ascending;
        return copy;
    }

    public SelectRaw orderBy(Column<?> column, boolean ascending) {
        return orderBy(column.name(), ascending);
    }

    public SelectRaw withDeleted() {
        SelectRaw copy = copy();
        copy.includeDeleted = true;
        return copy;
    }

    @Override
    protected String buildSql() {
        String colKey = selectedColumnNames == null || selectedColumnNames.isEmpty() ? "*" : selectedColumnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "selectraw:base:" + table.name() + ":" + colKey;
        
        Metadata meta = cache.getMetadata(cacheKey, _ -> {
            List<Column<?>> selected = (selectedColumnNames == null || selectedColumnNames.isEmpty())
                    ? table.columns()
                    : table.columns().stream().filter(c -> selectedColumnNames.contains(c.name())).collect(Collectors.toList());
            
            List<String> names = selected.stream().map(Column::name).collect(Collectors.toList());
            String sql = "SELECT " + String.join(", ", names) + " FROM " + table.name();
            return new Metadata(sql, names);
        });

        StringBuilder sql = new StringBuilder(meta.sqlBase);
        List<String> wheres = new ArrayList<>();
        
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql());
        
        Column<?> softDeleteCol = table.softDeleteColumn();
        if (softDeleteCol != null && !includeDeleted) {
            wheres.add(softDeleteCol.name() + " = " + dialect.softDeleteFalse());
        }

        if (!wheres.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", wheres));
        }

        if (orderBy != null) {
            sql.append(" ORDER BY ").append(orderBy).append(ascending ? " ASC" : " DESC");
        }

        if (dialect != null) {
            sql.append(dialect.limitOffsetSyntax(limit, offset));
        } else {
            if (limit >= 0) sql.append(" LIMIT ").append(limit);
            if (offset >= 0) sql.append(" OFFSET ").append(offset);
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
    public List<Row> execute(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return Row.fromResultSetAll(rs);
        }
    }
}
