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
import java.util.List;

public final class SelectEntity<T> extends AbstractQuery<List<T>> {

    private final Table<T> table;
    private final Object[] params;
    private final Metadata<T> metadata;

    private record Metadata<T>(String sql, Constructor<T> constructor, List<Column> columns) {}

    public SelectEntity(Table<T> table) {
        this(table, null, (Object[]) null);
    }

    public SelectEntity(final Table<T> table, final String whereClause, final Object... params) {
        this.table = table;
        this.params = params;

        String cacheKey = "select:meta:" + table.name() + ":" + (whereClause != null ? whereClause : "");
        this.metadata = QueryCache.getMetadata(cacheKey, _ -> {
            String sql = "SELECT * FROM " + table.name() + (whereClause != null && !whereClause.isEmpty() ? (" WHERE " + whereClause) : "");
            try {
                Constructor<T> constructor = table.clazz().getDeclaredConstructor();
                constructor.setAccessible(true);
                return new Metadata<>(sql, constructor, table.columns());
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Entity class " + table.clazz().getName() + " must have a no-args constructor", e);
            }
        });
    }

    private SelectEntity(Table<T> table, Metadata<T> metadata, Object[] params, boolean internal) {
        this.table = table;
        this.metadata = metadata;
        this.params = params;
    }

    /**
     * Creates a copy of this query with new parameters.
     * Used for efficient recycling of query prototypes.
     */
    public SelectEntity<T> copy(Object... params) {
        return new SelectEntity<>(table, metadata, params, true);
    }

    @Override
    protected String buildSql() {
        return metadata.sql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException {
        if (this.params == null) return;
        for (int i = 0; i < this.params.length; i++)
            ps.setObject(i + 1, this.params[i]);
    }

    @Override
    public List<T> execute(final PreparedStatement ps) throws SQLException {
        final ResultSet rs = ps.executeQuery();
        final List<T> result = new ArrayList<>();
        while (rs.next()) create(result, rs);
        return result;
    }

    private void create(List<T> result, ResultSet rs) throws SQLException {
        try {
            T obj = metadata.constructor.newInstance();
            for (Column c : metadata.columns) {
                Object value = rs.getObject(c.name());
                c.set(obj, value);
            }
            result.add(obj);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
