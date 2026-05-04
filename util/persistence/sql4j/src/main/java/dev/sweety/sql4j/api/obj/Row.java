package dev.sweety.sql4j.api.obj;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * A lightweight, ordered, immutable result row.
 *
 * <p>Used as the unified return type for raw/projection queries:
 * {@code SelectRaw}, {@code SelectJoin}, {@code QueryResult}, and {@code ParamQuery.rowBuilder()}.
 *
 * <p>Offers typed accessors for common Java types, with {@code null}-safe handling.
 */
public final class Row {

    private final Map<String, Object> data;

    public Row(Map<String, Object> data) {
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    // --- Typed accessors ---

    public <T> T get(String column) {
        //noinspection unchecked
        return (T) data.get(column.toLowerCase(Locale.ENGLISH));
    }

    public <T> T get(String column, Class<T> type) {
        return type.cast(get(column));
    }

    public String getString(String column) {
        Object v = get(column);
        return v == null ? null : v.toString();
    }

    public int getInt(String column) {
        Object v = get(column);
        return v instanceof Number n ? n.intValue() : 0;
    }

    public long getLong(String column) {
        Object v = get(column);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public double getDouble(String column) {
        Object v = get(column);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    public float getFloat(String column) {
        Object v = get(column);
        return v instanceof Number n ? n.floatValue() : 0.0f;
    }

    public boolean getBoolean(String column) {
        Object v = get(column);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }

    public boolean has(String column) {
        return data.containsKey(column.toLowerCase(Locale.ENGLISH));
    }

    public boolean isNull(String column) {
        return get(column) == null;
    }

    public Set<String> columns() {
        return data.keySet();
    }

    public Map<String, Object> toMap() {
        return data;
    }

    // --- Static factory ---

    /**
     * Reads a single row from the current position of a {@link ResultSet}.
     * Does NOT call {@code rs.next()} — the caller is responsible for cursor positioning.
     * Column names are normalized to lowercase.
     */
    public static Row fromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        Map<String, Object> map = new LinkedHashMap<>(count);
        for (int i = 1; i <= count; i++) {
            map.put(meta.getColumnLabel(i).toLowerCase(Locale.ENGLISH), rs.getObject(i));
        }
        return new Row(map);
    }

    /**
     * Reads all rows from a {@link ResultSet} into a list of {@link Row}.
     */
    public static List<Row> fromResultSetAll(ResultSet rs) throws SQLException {
        List<Row> rows = new ArrayList<>();
        while (rs.next()) rows.add(fromResultSet(rs));
        return rows;
    }

    @Override
    public String toString() {
        return "Row" + data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Row row)) return false;
        return data.equals(row.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
