package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Row-based SELECT query that returns {@link List}<{@link Row}> instead of entity instances.
 *
 * <p>Use this when you:
 * <ul>
 *   <li>Don't need a full entity object (lightweight reads)
 *   <li>Are selecting columns from multiple tables (e.g. JOIN projections)
 *   <li>Want typed access via {@code row.getString("name")}, {@code row.getInt("age")}, etc.
 * </ul>
 *
 * <p>SQL template is cached per (table, where, columns) triple.
 */
public final class SelectRaw extends AbstractQuery<List<Row>> {

    private final Object[] params;
    private final Metadata metadata;
    private final Dialect dialect;
    private final Table<?> table;

    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;
    private boolean includeDeleted = false;

    private record Metadata(String sql, List<String> columnNames, Table<?> table) {}

    // --- Constructors ---

    /** Selects all columns of the table, no WHERE. */
    public SelectRaw(Table<?> table, QueryCache cache, Dialect dialect) {
        this(table, null, null, cache, dialect, (Object[]) null);
    }

    /** Selects all columns with an optional WHERE clause. */
    public SelectRaw(Table<?> table, String whereClause, QueryCache cache, Dialect dialect, Object... params) {
        this(table, whereClause, null, cache, dialect, params);
    }

    /**
     * Selects specific columns from the table.
     *
     * @param table       entity table descriptor
     * @param whereClause optional WHERE clause (may be null)
     * @param columnNames explicit column names to select; null or empty → all columns
     * @param cache       query cache scoped to the owning Database
     * @param params      positional parameters for the WHERE clause
     */
    public SelectRaw(Table<?> table, String whereClause, Set<String> columnNames,
                     QueryCache cache, Dialect dialect, Object... params) {
        this.table = Objects.requireNonNull(table, "table cannot be null");
        this.params = params;
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        Objects.requireNonNull(cache, "cache cannot be null");

        String colKey = columnNames == null || columnNames.isEmpty()
                ? "*"
                : columnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "selectraw:meta:" + table.name()
                + ":" + (whereClause != null ? whereClause : "")
                + ":" + colKey;

        this.metadata = cache.getMetadata(cacheKey, _ -> buildMetadata(table, whereClause, columnNames));
    }

    /** Private copy constructor for prototype recycling. */
    private SelectRaw(Metadata metadata, Dialect dialect, Object[] params) {
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.params = params;
        this.table = metadata.table;
    }

    /** Creates a copy with new positional parameters — zero-allocation prototype reuse. */
    public SelectRaw copy(Object... params) {
        return new SelectRaw(metadata, dialect, params);
    }

    public SelectRaw limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectRaw offset(int offset) {
        this.offset = offset;
        return this;
    }

    public SelectRaw orderBy(String column, boolean ascending) {
        this.orderBy = column;
        this.ascending = ascending;
        return this;
    }

    public SelectRaw withDeleted() {
        this.includeDeleted = true;
        return this;
    }

    // --- Metadata builder ---

    private static Metadata buildMetadata(Table<?> table, String whereClause, Set<String> columnNames) {
        List<Column<?>> selected = columnNames == null || columnNames.isEmpty()
                ? table.columns()
                : table.columns().stream()
                        .filter(c -> columnNames.contains(c.name()))
                        .collect(Collectors.toList());

        if (selected.isEmpty())
            throw new IllegalArgumentException(
                    "No matching columns found for " + columnNames + " in table '" + table.name() + "'");

        List<String> names = selected.stream().map(Column::name).collect(Collectors.toList());
        String colList = String.join(", ", names);
        String sql = "SELECT " + colList + " FROM " + table.name()
                + (whereClause != null && !whereClause.isEmpty() ? " WHERE " + whereClause : "");

        return new Metadata(sql, names, table);
    }

    // --- Query implementation ---

    @Override
    protected String buildSql() {
        String base = metadata.sql;

        // Soft delete filtering
        Column<?> softDeleteCol = table.softDeleteColumn();
        if (softDeleteCol != null && !includeDeleted) {
            String filter;
            if (softDeleteCol.type() == java.time.LocalDateTime.class || softDeleteCol.type() == java.util.Date.class) {
                filter = softDeleteCol.name() + " IS NULL";
            } else {
                filter = softDeleteCol.name() + " = 0";
            }

            if (base.toUpperCase(Locale.ENGLISH).contains(" WHERE ")) {
                base += " AND " + filter;
            } else {
                base += " WHERE " + filter;
            }
        }

        if (orderBy != null) {
            base += " ORDER BY " + orderBy + (ascending ? " ASC" : " DESC");
        }
        if (dialect != null) {
            base += dialect.limitOffsetSyntax(limit, offset);
        } else if (limit >= 0) {
            base += " LIMIT " + limit;
            if (offset >= 0) base += " OFFSET " + offset;
        }
        return base;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    @Override
    public List<Row> execute(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return Row.fromResultSetAll(rs);
        }
    }
}
