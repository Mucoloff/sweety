package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.Objects;

public final class SelectJoin extends AbstractQuery<List<Row>> {

    private final List<Table<?>> tables;
    private final String sql;
    private final List<Object> params;
    private final Dialect dialect;

    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;

    /**
     * @param tables      tabelle da joinare
     * @param onClauses   clausole ON per ogni join (tables.size() - 1 elementi)
     * @param whereClause eventuale WHERE
     * @param dialect     dialetto del database
     * @param params      parametri della query
     */
    private SelectJoin(List<Table<?>> tables, List<String> onClauses, String whereClause, Dialect dialect, Object... params) {
        Objects.requireNonNull(tables, "tables cannot be null");
        Objects.requireNonNull(onClauses, "onClauses cannot be null");
        if (tables.size() < 2)
            throw new IllegalArgumentException("Servono almeno 2 tabelle per un join");
        if (onClauses.size() != tables.size() - 1)
            throw new IllegalArgumentException("Numero di onClauses deve essere " + (tables.size() - 1));

        this.tables = tables;
        this.params = Arrays.asList(params != null ? params : new Object[0]);
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.sql = buildJoinSql(tables, onClauses, whereClause);
    }

    private String buildJoinSql(List<Table<?>> tables, List<String> onClauses, String whereClause) {
        StringBuilder sb = new StringBuilder("SELECT ");

        List<String> cols = new ArrayList<>();
        for (Table<?> t : tables) {
            for (Column c : t.columns()) {
                cols.add(t.name() + "." + c.name() + " AS " + t.name() + "_" + c.name());
            }
        }
        sb.append(String.join(", ", cols));

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
        String base = sql;
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

    public SelectJoin limit(int limit) {
        this.limit = limit;
        return this;
    }

    public SelectJoin offset(int offset) {
        this.offset = offset;
        return this;
    }

    public SelectJoin orderBy(String column, boolean ascending) {
        this.orderBy = column;
        this.ascending = ascending;
        return this;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    @Override
    public List<Row> execute(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return Row.fromResultSetAll(rs);
        }
    }

    // --- Estrazione Avanzata (Relazioni) ---

    /**
     * Mappa i risultati del JOIN in una lista di oggetti generici.
     * Utile per ricostruire alberi di entità (es. User con List<Order>) estraendo 
     * le singole parti usando `row.extractEntity(...)`.
     */
    public <R> Query<List<R>> extractObjects(Function<Row, R> mapper) {
        return new AbstractQuery<>() {
            @Override
            protected String buildSql() {
                return SelectJoin.this.sql();
            }

            @Override
            public void bind(PreparedStatement ps) throws SQLException {
                SelectJoin.this.bind(ps);
            }

            @Override
            public List<R> execute(PreparedStatement ps) throws SQLException {
                return SelectJoin.this.execute(ps).stream().map(mapper).toList();
            }
        };
    }

    /**
     * Tenta di mappare automaticamente i risultati sull'albero di dipendenze.
     * Raggruppa le righe per la chiave primaria della radice e popola le liste @OneToMany.
     */
    public <R> Query<List<R>> mapToHierarchy(Class<R> type) {
        return new AbstractQuery<>() {
            @Override protected String buildSql() { return SelectJoin.this.sql(); }
            @Override public void bind(PreparedStatement ps) throws SQLException { SelectJoin.this.bind(ps); }

            @Override
            public List<R> execute(PreparedStatement ps) throws SQLException {
                List<Row> rows = SelectJoin.this.execute(ps);
                if (rows.isEmpty()) return Collections.emptyList();

                // 1. Trova la tabella radice
                Table<R> rootTableFound = null;
                for (Table<?> t : tables) {
                    if (t.clazz() == type) {
                        //noinspection unchecked
                        rootTableFound = (Table<R>) t;
                        break;
                    }
                }
                if (rootTableFound == null) throw new IllegalArgumentException("Root type " + type.getName() + " not found in JOIN tables");
                
                final Table<R> rootTable = rootTableFound;
                final String rootPrefix = rootTable.name();

                Map<Object, R> identityMap = new LinkedHashMap<>();

                for (Row row : rows) {
                    Object pkValue = row.get(rootPrefix + "_" + rootTable.primaryKeys().get(0).name());
                    if (pkValue == null) continue;

                    R root = identityMap.computeIfAbsent(pkValue, _ -> row.extractEntity(rootTable, rootPrefix));

                    // 2. Popola le relazioni per ogni tabella coinvolta nel JOIN
                    for (Table<?> t : tables) {
                        if (t == rootTable) continue;
                        String prefix = t.name();
                        
                        // Controlla se questa tabella è una relazione della radice
                        for (Table.Relation rel : rootTable.relations()) {
                            if (rel.targetClass() == t.clazz()) {
                                populateRelation(root, rel, t, row, prefix);
                            }
                        }
                    }
                }
                return new ArrayList<>(identityMap.values());
            }

            private void populateRelation(Object root, Table.Relation rel, Table<?> targetTable, Row row, String prefix) {
                try {
                    rel.field().setAccessible(true);
                    Object targetPk = row.get(prefix + "_" + targetTable.primaryKeys().get(0).name());
                    if (targetPk == null) return; // Null side of LEFT JOIN or no data

                    Object targetEntity = row.extractEntity(targetTable, prefix);
                    if (targetEntity == null) return; // Should not happen if targetPk was not null but extra safety

                    if (rel.type() == Table.Relation.Type.ONE_TO_MANY || rel.type() == Table.Relation.Type.MANY_TO_MANY) {
                        //noinspection unchecked
                        Collection<Object> collection = (Collection<Object>) rel.field().get(root);
                        if (collection == null) {
                            if (rel.field().getType() == Set.class) collection = new HashSet<>();
                            else collection = new ArrayList<>();
                            rel.field().set(root, collection);
                        }
                        // Simple deduplication for collections based on PK (manual check here since we don't have equals/hashCode on entities)
                        boolean exists = false;
                        for (Object existing : collection) {
                            Object existingPk = targetTable.primaryKeys().get(0).get(existing);
                            if (Objects.equals(existingPk, targetPk)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) collection.add(targetEntity);
                    } else if (rel.type() == Table.Relation.Type.MANY_TO_ONE) {
                        if (rel.field().get(root) == null) {
                            rel.field().set(root, targetEntity);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to populate hierarchy for " + rel.field().getName(), e);
                }
            }
        };
    }

    public static class Builder {
        private final List<Table<?>> tablesList = new ArrayList<>();
        private final Set<Table<?>> tablesSet = new HashSet<>();

        private final List<String> onClausesList = new ArrayList<>();
        private final Set<String> onClausesSet = new HashSet<>();

        private String whereClause = null;
        private final List<Object> params = new ArrayList<>();
        private Dialect dialect = null;

        public Builder() {}

        public Builder dialect(Dialect dialect) {
            this.dialect = dialect;
            return this;
        }

        public Builder join(Table<?>... tables) {
            for (Table<?> t : tables) {
                if (tablesSet.add(t)) {
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

        public Builder on(Column left, Column right) {
            String clause = left.table().name() + "." + left.name() + " = " + right.table().name() + "." + right.name();
            if (onClausesSet.add(clause)) {
                onClausesList.add(clause);
            }
            return this;
        }

        public Builder where(String whereClause, Object... params) {
            this.whereClause = whereClause;
            Collections.addAll(this.params, params);
            return this;
        }

        public SelectJoin build() {
            return new SelectJoin(tablesList, onClausesList, whereClause, dialect, params.toArray());
        }
    }
}
