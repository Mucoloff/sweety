package dev.sweety.sql4j.impl.query.entity;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SelectEntity<T> extends AbstractQuery<List<T>> {

    private final Table<T> table;
    private final Object[] params;

    private final String sql;

    public SelectEntity(Table<T> table) {
        this(table, null, (Object[]) null);
    }

    public SelectEntity(final Table<T> table, final String whereClause, final Object... params) {
        this.table = table;
        this.params = params;
        this.sql = "SELECT * FROM " + table.name() + (whereClause != null && !whereClause.isEmpty() ? (" WHERE " + whereClause) : "");
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    @Override
    public void bind(final PreparedStatement ps) throws SQLException{
        if (this.params == null) return;
        for (int i = 0; i < this.params.length; i++)
            ps.setObject(i + 1, this.params[i]);
    }

    @Override
    public List<T> execute(final PreparedStatement ps) throws SQLException {
        final ResultSet rs = ps.executeQuery();
        final List<T> result = new ArrayList<>(rs.getFetchSize());
        while (rs.next()) create(result, rs);
        return result;
    }

    private void create(List<T> result, ResultSet rs) throws SQLException {
        try {
            T obj = this.table.clazz().getDeclaredConstructor().newInstance();
            for (Column c : this.table.columns()) {
                Object value = rs.getObject(c.name());
                c.set(obj, value);
            }
            result.add(obj);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }
}
