package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.Query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

public final class SelectJoin extends AbstractQuery<List<Row>> {

    private final List<Table<?>> tables;
    private final List<JoinInfo> joins;
    private final String sql;
    private final List<Object> params;
    private final Criterion criterion;
    private final Dialect dialect;

    private int limit = -1;
    private int offset = -1;
    private String orderBy = null;
    private boolean ascending = true;

    private record JoinInfo(Table<?> sourceTable, Table<?> targetTable, Table.Relation relation) {}

    private SelectJoin(List<Table<?>> tables, List<JoinInfo> joins, List<String> onClauses, String whereClause, Criterion criterion, Dialect dialect, Object... params) {
        this.tables = tables;
        this.joins = joins;
        this.params = Arrays.asList(params != null ? params : new Object[0]);
        this.criterion = criterion;
        this.dialect = dialect;
        this.sql = buildJoinSql(tables, onClauses, whereClause, criterion);
    }

    private String buildJoinSql(List<Table<?>> tables, List<String> onClauses, String whereClause, Criterion criterion) {
        StringBuilder sb = new StringBuilder("SELECT ");
        List<String> cols = new ArrayList<>();
        for (Table<?> t : tables) {
            for (Column<?> c : t.columns()) {
                cols.add(t.name() + "." + c.name() + " AS " + t.name() + "_" + c.name());
            }
        }
        sb.append(String.join(", ", cols));

        sb.append(" FROM ").append(tables.get(0).name());
        for (int i = 1; i < tables.size(); i++) {
            sb.append(" INNER JOIN ").append(tables.get(i).name())
                    .append(" ON ").append(onClauses.get(i - 1));
        }

        List<String> wheres = new ArrayList<>();
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql());

        if (!wheres.isEmpty()) {
            sb.append(" WHERE ").append(String.join(" AND ", wheres));
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
        int idx = 1;
        for (Object p : params) {
            ps.setObject(idx++, p);
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

    public <R> Query<List<R>> mapToHierarchy(Class<R> type) {
        return new AbstractQuery<>() {
            @Override protected String buildSql() { return SelectJoin.this.sql(); }
            @Override public void bind(PreparedStatement ps) throws SQLException { SelectJoin.this.bind(ps); }
            @Override public List<R> execute(PreparedStatement ps) throws SQLException {
                List<Row> rows = SelectJoin.this.execute(ps);
                if (rows.isEmpty()) return Collections.emptyList();

                Table<R> rootTableFound = null;
                for (Table<?> t : tables) {
                    if (t.clazz() == type) {
                        //noinspection unchecked
                        rootTableFound = (Table<R>) t;
                        break;
                    }
                }
                if (rootTableFound == null) throw new IllegalArgumentException("Root type " + type.getName() + " not found");
                final Table<R> rootTable = rootTableFound;
                
                Map<Object, R> identityMap = new LinkedHashMap<>();
                // identity maps for each table to ensure we reuse instances within the same result set
                Map<Table<?>, Map<Object, Object>> globalIdentityMap = new HashMap<>();
                for (Table<?> t : tables) globalIdentityMap.put(t, new HashMap<>());

                for (Row row : rows) {
                    Object rootPk = row.get(rootTable.name() + "_" + rootTable.primaryKeys().get(0).name());
                    if (rootPk == null) continue;

                    R root = (R) globalIdentityMap.get(rootTable).computeIfAbsent(rootPk, _ -> row.extractEntity(rootTable, rootTable.name()));
                    identityMap.put(rootPk, root);

                    for (JoinInfo join : joins) {
                        Object sourcePk = row.get(join.sourceTable.name() + "_" + join.sourceTable.primaryKeys().get(0).name());
                        Object targetPk = row.get(join.targetTable.name() + "_" + join.targetTable.primaryKeys().get(0).name());
                        
                        if (sourcePk == null || targetPk == null) continue;

                        Object sourceEntity = globalIdentityMap.get(join.sourceTable).get(sourcePk);
                        if (sourceEntity == null) continue; // Should not happen if join list is ordered correctly

                        Object targetEntity = globalIdentityMap.get(join.targetTable).computeIfAbsent(targetPk, _ -> row.extractEntity(join.targetTable, join.targetTable.name()));
                        
                        populateRelation(sourceEntity, join.relation, join.targetTable, targetEntity, targetPk);
                    }
                }
                return new ArrayList<>(identityMap.values());
            }

            private void populateRelation(Object source, Table.Relation rel, Table<?> targetTable, Object targetEntity, Object targetPk) {
                try {
                    rel.field().setAccessible(true);
                    if (rel.type() == Table.Relation.Type.ONE_TO_MANY || rel.type() == Table.Relation.Type.MANY_TO_MANY) {
                        //noinspection unchecked
                        Collection<Object> collection = (Collection<Object>) rel.field().get(source);
                        if (collection == null) {
                            collection = (rel.field().getType() == Set.class) ? new HashSet<>() : new ArrayList<>();
                            rel.field().set(source, collection);
                        }
                        boolean exists = false;
                        for (Object existing : collection) {
                            if (Objects.equals(targetTable.primaryKeys().get(0).get(existing), targetPk)) {
                                exists = true; break;
                            }
                        }
                        if (!exists) collection.add(targetEntity);
                    } else {
                        if (rel.field().get(source) == null) rel.field().set(source, targetEntity);
                    }
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        };
    }

    public static class Builder {
        private final List<Table<?>> tablesList = new ArrayList<>();
        private final List<JoinInfo> joinsList = new ArrayList<>();
        private final List<String> onClausesList = new ArrayList<>();
        private final TableRegistry registry;
        private String whereClause;
        private Criterion criterion;
        private final List<Object> params = new ArrayList<>();
        private Dialect dialect;

        public Builder() {
            this(TableRegistry.getDefault());
        }

        public Builder(TableRegistry registry) {
            this.registry = registry;
        }

        public Builder dialect(Dialect dialect) { this.dialect = dialect; return this; }

        public Builder join(Table<?>... tables) {
            for (Table<?> table : tables) {
                if (!tablesList.contains(table)) tablesList.add(table);
            }
            return this;
        }

        public Builder join(Table.Relation rel) {
            Table<?> targetTable = registry.get(rel.targetClass());
            
            // Find the source table: the one that declares this relation
            Class<?> declaringClass = rel.field().getDeclaringClass();
            Table<?> sourceTable = null;
            for (Table<?> t : tablesList) {
                if (t.clazz().isAssignableFrom(declaringClass)) {
                    sourceTable = t;
                    break;
                }
            }
            if (sourceTable == null && tablesList.size() >= 1) {
                sourceTable = tablesList.get(tablesList.size() - 1);
            }

            if (sourceTable == null) {
                throw new IllegalStateException("Source table for relation " + rel.field().getName() + " not found in join builder");
            }

            if (!tablesList.contains(targetTable)) tablesList.add(targetTable);
            joinsList.add(new JoinInfo(sourceTable, targetTable, rel));

            // ManyToOne -> source.fk == target.pk
            if (rel.type() == Table.Relation.Type.MANY_TO_ONE) {
                on(sourceTable.column(rel.column().name()), targetTable.primaryKeys().get(0));
            } else if (rel.type() == Table.Relation.Type.ONE_TO_MANY) {
                // OneToMany -> source.pk == target.column(rel.mappedBy())
                on(sourceTable.primaryKeys().get(0), targetTable.column(rel.mappedBy()));
            }
            return this;
        }

        public Builder on(String... onClauses) {
            Collections.addAll(onClausesList, onClauses);
            return this;
        }

        public Builder on(Column<?> left, Column<?> right) {
            onClausesList.add(left.table().name() + "." + left.name() + " = " + right.table().name() + "." + right.name());
            return this;
        }

        public Builder where(String whereClause, Object... params) {
            this.whereClause = whereClause;
            Collections.addAll(this.params, params);
            return this;
        }

        public Builder where(Criterion criterion) {
            this.criterion = criterion;
            return this;
        }

        public SelectJoin build() {
            return new SelectJoin(tablesList, joinsList, onClausesList, whereClause, criterion, dialect, params.toArray());
        }
    }
}
