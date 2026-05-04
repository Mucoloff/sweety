package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.impl.query.QueryCache;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entity-based SELECT query that returns fully populated (or partially populated) entity instances.
 *
 * <p>Two main modes:
 * <ul>
 *   <li><b>Full</b> — {@code SELECT col1, col2, ... FROM table} — all columns fetched.
 *   <li><b>Projection</b> — {@code SELECT name, age FROM table} — only the specified columns are
 *       fetched; unselected fields are left at their zero/null default value.
 * </ul>
 *
 * <p>SQL templates and constructor handles are cached per (table, where, columns) triple.
 */
public final class SelectEntity<T> extends AbstractQuery<List<T>> {

    private final Table<T> table;
    private final Object[] params;
    private final Metadata<T> metadata;

    private record Metadata<T>(String sql, Constructor<T> constructor, List<Column> selectedColumns) {}

    // --- Constructors ---

    /** Full select, no WHERE. */
    public SelectEntity(Table<T> table, QueryCache cache) {
        this(table, null, null, cache, (Object[]) null);
    }

    /** Full select, optional WHERE. */
    public SelectEntity(Table<T> table, String whereClause, QueryCache cache, Object... params) {
        this(table, whereClause, null, cache, params);
    }

    /**
     * Projection select — only the specified columns are included in the SQL.
     * Unspecified fields in the entity will be left at zero/null.
     *
     * @param table        entity table descriptor
     * @param whereClause  optional WHERE clause (may be null)
     * @param columnNames  explicit column names to include; null or empty → all columns
     * @param cache        query cache scoped to the owning Database
     * @param params       positional parameters for the WHERE clause
     */
    public SelectEntity(Table<T> table, String whereClause, Set<String> columnNames,
                        QueryCache cache, Object... params) {
        this.table = table;
        this.params = params;

        // Build cache key from table + where + sorted column names for stable identity
        String colKey = columnNames == null || columnNames.isEmpty()
                ? "*"
                : columnNames.stream().sorted().collect(Collectors.joining(","));
        String cacheKey = "select:meta:" + table.name()
                + ":" + (whereClause != null ? whereClause : "")
                + ":" + colKey;

        this.metadata = cache.getMetadata(cacheKey, _ -> buildMetadata(table, whereClause, columnNames));
    }

    /** Private copy constructor (for prototype recycling). */
    private SelectEntity(Table<T> table, Metadata<T> metadata, Object[] params) {
        this.table = table;
        this.metadata = metadata;
        this.params = params;
    }

    /** Creates a copy with new positional parameters — zero-allocation prototype reuse. */
    public SelectEntity<T> copy(Object... params) {
        return new SelectEntity<>(table, metadata, params);
    }

    // --- Metadata builder ---

    private static <T> Metadata<T> buildMetadata(Table<T> table, String whereClause, Set<String> columnNames) {
        List<Column> selected = columnNames == null || columnNames.isEmpty()
                ? table.columns()
                : table.columns().stream()
                        .filter(c -> columnNames.contains(c.name()))
                        .toList();

        if (selected.isEmpty())
            throw new IllegalArgumentException(
                    "No matching columns found for " + columnNames + " in table '" + table.name() + "'");

        String colList = selected.stream().map(Column::name).collect(Collectors.joining(", "));
        String sql = "SELECT " + colList + " FROM " + table.name()
                + (whereClause != null && !whereClause.isEmpty() ? " WHERE " + whereClause : "");

        try {
            Constructor<T> ctor = table.clazz().getDeclaredConstructor();
            ctor.setAccessible(true);
            return new Metadata<>(sql, ctor, selected);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "Entity class '" + table.clazz().getName() + "' must have a no-args constructor", e);
        }
    }

    // --- Query implementation ---

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
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
