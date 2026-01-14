package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class SelectJoin extends AbstractQuery<List<Map<String, Object>>> {

    private final String sql;
    private final List<Object> params;

    /**
     * @param tables      tabelle da joinare
     * @param onClauses   clausole ON per ogni join (tables.size() - 1 elementi)
     * @param whereClause eventuale WHERE
     * @param params      parametri della query
     */
    private SelectJoin(List<Table<?>> tables, List<String> onClauses, String whereClause, Object... params) {
        if (tables.size() < 2)
            throw new IllegalArgumentException("Serve almeno 2 tabelle per un join");
        if (onClauses.size() != tables.size() - 1)
            throw new IllegalArgumentException("Numero di onClauses deve essere tables.size() - 1");

        this.params = Arrays.asList(params);
        this.sql = buildSql(tables, onClauses, whereClause);
    }

    private String buildSql(List<Table<?>> tables, List<String> onClauses, String whereClause) {
        StringBuilder sb = new StringBuilder("SELECT ");

        // colonne con alias
        List<String> cols = new ArrayList<>();
        for (Table<?> t : tables) {
            for (Column c : t.columns()) {
                cols.add(t.name() + "." + c.name() + " AS " + t.name() + "_" + c.name());
            }
        }
        sb.append(String.join(", ", cols));

        // from + join
        sb.append(" FROM ").append(tables.getFirst().name());
        for (int i = 1; i < tables.size(); i++) {
            sb.append(" INNER JOIN ").append(tables.get(i).name())
                    .append(" ON ").append(onClauses.get(i - 1));
        }

        if (whereClause != null && !whereClause.isEmpty()) {
            sb.append(" WHERE ").append(whereClause);
        }

        return sb.toString();
    }

    @Override
    protected String buildSql() {
        return sql;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    @Override
    public List<Map<String, Object>> execute(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    public static class Builder {
        private final List<Table<?>> tablesList = new ArrayList<>();
        private final Set<Table<?>> tablesSet = new HashSet<>();

        private final List<String> onClausesList = new ArrayList<>();
        private final Set<String> onClausesSet = new HashSet<>();

        private String whereClause = null;
        private final List<Object> params = new ArrayList<>();

        public Builder() {

        }

        public Builder join(Table<?>... tables) {
            for (Table<?> t : tables) {
                if (tablesSet.add(t)) { // se non c’era già
                    tablesList.add(t);
                }
            }
            return this;
        }

        public Builder on(String... onClauses) {
            for (String c : onClauses) {
                if (onClausesSet.add(c)) {
                    onClausesList.add(c);
                }
            }
            return this;
        }

        public Builder where(String whereClause, Object... params) {
            this.whereClause = whereClause;
            Collections.addAll(this.params, params);
            return this;
        }

        public SelectJoin build() {
            if (tablesList.isEmpty()) throw new IllegalStateException("Deve essere specificata almeno una tabella");
            if (onClausesList.isEmpty()) throw new IllegalStateException("Deve essere specificata almeno una clausola ON");
            if (whereClause == null || whereClause.isEmpty())
                throw new IllegalStateException("Deve essere specificata una clausola WHERE");
            if (params.isEmpty())
                throw new IllegalStateException("Devono essere specificati i parametri della clausola WHERE");

            return new SelectJoin(tablesList, onClausesList, whereClause, params.toArray());
        }
    }

}
