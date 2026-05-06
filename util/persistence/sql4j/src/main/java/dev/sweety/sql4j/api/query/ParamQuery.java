package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A query that executes raw SQL with parameters and maps the results.
 */
public final class ParamQuery<T> extends AbstractQuery<T> {

    private final String sql;
    private final List<Object> params;
    private final Function<Row, ?> mapper;
    private final Table<?> table;

    public ParamQuery(String sql, List<Object> params, Table<T> table) {
        this.sql = sql;
        this.params = params;
        this.table = table;
        this.mapper = null;
    }

    public ParamQuery(String sql, List<Object> params, Function<Row, T> mapper) {
        this.sql = sql;
        this.params = params;
        this.mapper = mapper;
        this.table = null;
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T execute(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Row> rows = Row.fromResultSetAll(rs);
            if (table != null) {
                List<Object> entities = new ArrayList<>();
                for (Row row : rows) {
                    entities.add(row.extractEntity((Table<Object>) table, ""));
                }
                return (T) entities;
            } else if (mapper != null) {
                List<Object> results = new ArrayList<>();
                for (Row row : rows) {
                    results.add(mapper.apply(row));
                }
                return (T) results;
            } else {
                return (T) rows;
            }
        }
    }
}
