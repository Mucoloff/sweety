package dev.sweety.sql4j.impl.query;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.exception.Sql4jMappingException;
import dev.sweety.sql4j.api.exception.Sql4jQueryException;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.obj.table.TableRegistry;
import dev.sweety.sql4j.api.query.AbstractQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.JoinBuilder;
import dev.sweety.sql4j.api.query.Query;

import dev.sweety.sql4j.api.connection.SqlConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class SelectJoin extends AbstractQuery<List<Row>> {

    private final List<Table<?>> tables;
    private final List<JoinInfo> joins;
    private final String sql;
    private final List<Object> params;
    private final Criterion criterion;
    private final Dialect dialect;

    private final String fromAndJoinsSql;
    private final String whereSql;
    private final String selectColsSql;

    private final int limit;
    private final int offset;
    private final String orderBy;
    private final boolean ascending;
    private final boolean includeDeleted;
    private final List<Column<?>> groupByColumns;
    private final Criterion havingCriterion;

    private record JoinInfo(Table<?> sourceTable, Table<?> targetTable, Table.Relation relation) {}

    private SelectJoin(List<Table<?>> tables, List<JoinInfo> joins, List<String> onClauses, String whereClause, Criterion criterion, Dialect dialect, boolean includeDeleted, List<Column<?>> groupByColumns, Criterion havingCriterion, int limit, int offset, String orderBy, boolean ascending, Object... params) {
        this.tables = tables;
        this.joins = joins;
        this.params = Arrays.asList(params != null ? params : new Object[0]);
        this.criterion = criterion;
        this.dialect = dialect;
        this.includeDeleted = includeDeleted;
        this.groupByColumns = groupByColumns;
        this.havingCriterion = havingCriterion;
        this.limit = limit;
        this.offset = offset;
        this.orderBy = orderBy;
        this.ascending = ascending;

        // Build SELECT columns
        List<String> cols = new ArrayList<>();
        for (Table<?> t : tables) {
            for (Column<?> c : t.columns()) {
                cols.add(dialect.escape(t.name()) + "." + dialect.escape(c.name()) + " AS " + dialect.escape(t.name() + "_" + c.name()));
            }
        }
        this.selectColsSql = String.join(", ", cols);

        // Build FROM and JOINS
        StringBuilder fj = new StringBuilder(" FROM ").append(dialect.escape(tables.get(0).name()));
        for (int i = 1; i < tables.size(); i++) {
            fj.append(" INNER JOIN ").append(dialect.escape(tables.get(i).name()))
                    .append(" ON ").append(onClauses.get(i - 1));
        }
        this.fromAndJoinsSql = fj.toString();

        // Build WHERE
        List<String> wheres = new ArrayList<>();
        if (whereClause != null && !whereClause.isEmpty()) wheres.add(whereClause);
        if (criterion != null) wheres.add(criterion.toSql(dialect));
        
        // Global Soft Delete Filter
        if (!includeDeleted) {
            for (Table<?> t : tables) {
                Column<?> sd = t.softDeleteColumn();
                if (sd != null) {
                    wheres.add(dialect.escape(t.name()) + "." + dialect.escape(sd.name()) + " = " + dialect.softDeleteFalse());
                }
            }
        }
        
        this.whereSql = wheres.isEmpty() ? "" : " WHERE " + String.join(" AND ", wheres);

        this.sql = "SELECT " + selectColsSql + fromAndJoinsSql + whereSql;
    }

    public String countSql() {
        Table<?> root = tables.get(0);
        return "SELECT COUNT(DISTINCT " + dialect.escape(root.name()) + "." + dialect.escape(root.primaryKeys().get(0).name()) + ")" + fromAndJoinsSql + whereSql;
    }

    @Override
    protected String buildSql() {
        StringBuilder sb = new StringBuilder(sql);
        
        if (groupByColumns != null && !groupByColumns.isEmpty()) {
            sb.append(" GROUP BY ").append(groupByColumns.stream()
                .map(c -> dialect.escape(c.table().name()) + "." + dialect.escape(c.name()))
                .collect(Collectors.joining(", ")));
        }

        if (havingCriterion != null) {
            sb.append(" HAVING ").append(havingCriterion.toSql(dialect));
        }

        if (orderBy != null) {
            sb.append(" ORDER BY ").append(dialect.escape(orderBy)).append(ascending ? " ASC" : " DESC");
        }
        if (dialect != null) {
            sb.append(dialect.limitOffsetSyntax(limit, offset));
        } else if (limit >= 0) {
            sb.append(" LIMIT ").append(limit);
            if (offset >= 0) sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    // Actually, a better way is to use a private copy constructor or similar.
    private SelectJoin(SelectJoin other, int limit, int offset, String orderBy, boolean ascending) {
        this.tables = other.tables;
        this.joins = other.joins;
        this.sql = other.sql;
        this.params = other.params;
        this.criterion = other.criterion;
        this.dialect = other.dialect;
        this.fromAndJoinsSql = other.fromAndJoinsSql;
        this.whereSql = other.whereSql;
        this.selectColsSql = other.selectColsSql;
        this.limit = limit;
        this.offset = offset;
        this.orderBy = orderBy;
        this.ascending = ascending;
        this.includeDeleted = other.includeDeleted;
        this.groupByColumns = other.groupByColumns;
        this.havingCriterion = other.havingCriterion;
    }

    public SelectJoin limit(int limit) {
        return new SelectJoin(this, limit, this.offset, this.orderBy, this.ascending);
    }

    public SelectJoin offset(int offset) {
        return new SelectJoin(this, this.limit, offset, this.orderBy, this.ascending);
    }

    public SelectJoin orderBy(String column, boolean ascending) {
        return new SelectJoin(this, this.limit, this.offset, column, ascending);
    }

    public CompletableFuture<List<Row>> executeAggregate(SqlConnection con) {
        return con.executeAsync(this);
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
        if (havingCriterion != null) {
            havingCriterion.bind(ps, idx + (criterion != null ? criterion.countParameters() : 0));
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
                if (rootTableFound == null) throw new Sql4jMappingException("Root type " + type.getName() + " not found in join builder");
                final Table<R> rootTable = rootTableFound;
                
                Map<Object, R> identityMap = new LinkedHashMap<>();
                // identity maps for each table to ensure we reuse instances within the same result set
                Map<Table<?>, Map<Object, Object>> globalIdentityMap = new HashMap<>();
                for (Table<?> t : tables) globalIdentityMap.put(t, new HashMap<>());

                // (relation, sourcePk) -> already-attached target PKs. Was a linear scan over the
                // live collection per row (O(m) per row, O(n*m) total across a relation) — this
                // makes the ONE_TO_MANY/MANY_TO_MANY dedup check O(1) per row instead.
                Map<Table.Relation, Map<Object, Set<Object>>> seenTargetsByRelation = new HashMap<>();

                for (Row row : rows) {
                    Object rootPk = row.get(rootTable.name() + "_" + rootTable.primaryKeys().get(0).name());
                    if (rootPk == null) continue;

                    //noinspection unchecked
                    R root = (R) globalIdentityMap.get(rootTable).computeIfAbsent(rootPk, ignored -> row.extractEntity(rootTable, rootTable.name()));
                    identityMap.put(rootPk, root);

                    for (JoinInfo join : joins) {
                        Object sourcePk = row.get(join.sourceTable.name() + "_" + join.sourceTable.primaryKeys().get(0).name());
                        Object targetPk = row.get(join.targetTable.name() + "_" + join.targetTable.primaryKeys().get(0).name());
                        
                        if (sourcePk == null || targetPk == null) continue;

                        Object sourceEntity = globalIdentityMap.get(join.sourceTable).get(sourcePk);
                        if (sourceEntity == null) continue; // Should not happen if join list is ordered correctly

                        Object targetEntity = globalIdentityMap.get(join.targetTable).computeIfAbsent(targetPk, ignored -> row.extractEntity(join.targetTable, join.targetTable.name()));

                        populateRelation(sourceEntity, sourcePk, join.relation, targetEntity, targetPk, seenTargetsByRelation);
                    }
                }
                return new ArrayList<>(identityMap.values());
            }

            private void populateRelation(Object source, Object sourcePk, Table.Relation rel, Object targetEntity, Object targetPk,
                                           Map<Table.Relation, Map<Object, Set<Object>>> seenTargetsByRelation) {
                try {
                    rel.field().setAccessible(true);
                    switch (rel.type()) {
                        case ONE_TO_MANY, MANY_TO_MANY -> {
                            //noinspection unchecked
                            Collection<Object> collection = (Collection<Object>) rel.field().get(source);
                            if (collection == null) {
                                collection = (rel.field().getType() == Set.class) ? new HashSet<>() : new ArrayList<>();
                                rel.field().set(source, collection);
                            }
                            Set<Object> seenTargets = seenTargetsByRelation
                                    .computeIfAbsent(rel, ignored -> new HashMap<>())
                                    .computeIfAbsent(sourcePk, ignored -> new HashSet<>());
                            if (seenTargets.add(targetPk)) collection.add(targetEntity);
                        }
                        case MANY_TO_ONE -> {
                            if (rel.field().get(source) == null) rel.field().set(source, targetEntity);
                        }
                    }
                } catch (Sql4jMappingException e) {
                    throw e;
                } catch (Exception e) {
                    throw new Sql4jMappingException("Failed to populate relation field '" + rel.field().getName() + "'", e);
                }
            }
        };
    }

    public static class Builder implements JoinBuilder {
        private final LinkedHashSet<Table<?>> tablesList = new LinkedHashSet<>();
        private Table<?> lastTable;
        private final List<JoinInfo> joinsList = new ArrayList<>();
        private final List<String> onClausesList = new ArrayList<>();
        private final TableRegistry registry;
        private String whereClause;
        private Criterion criterion;
        private final List<Object> params = new ArrayList<>();
        private Dialect dialect;
        private boolean includeDeleted = false;
        private List<Column<?>> groupByColumns;
        private Criterion havingCriterion;

        public Builder() {
            this(TableRegistry.getDefault());
        }

        public Builder(TableRegistry registry) {
            this.registry = registry;
        }

        public Builder dialect(Dialect dialect) { this.dialect = dialect; return this; }

        public Builder join(Table<?>... tables) {
            for (Table<?> table : tables) {
                if (tablesList.add(table)) lastTable = table;
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
            if (sourceTable == null && !tablesList.isEmpty()) {
                sourceTable = lastTable;
            }

            if (sourceTable == null) {
                throw new Sql4jMappingException("Source table for relation '" + rel.field().getName() + "' not found in join builder");
            }

            if (tablesList.add(targetTable)) lastTable = targetTable;
            joinsList.add(new JoinInfo(sourceTable, targetTable, rel));

            switch (rel.type()) {
                case MANY_TO_ONE ->
                    // source.fk == target.pk
                    on(sourceTable.column(rel.column().name()), targetTable.primaryKeys().get(0));
                case ONE_TO_MANY ->
                    // source.pk == target.fk(mappedBy)
                    on(sourceTable.primaryKeys().get(0), targetTable.column(rel.mappedBy()));
                case MANY_TO_MANY -> { /* junction table ON clause handled separately */ }
            }
            return this;
        }

        public Builder on(String... onClauses) {
            Collections.addAll(onClausesList, onClauses);
            return this;
        }

        public Builder on(Column<?> left, Column<?> right) {
            onClausesList.add(dialect.escape(left.table().name()) + "." + dialect.escape(left.name()) + " = " + dialect.escape(right.table().name()) + "." + dialect.escape(right.name()));
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

        public Builder includeDeleted(boolean includeDeleted) {
            this.includeDeleted = includeDeleted;
            return this;
        }

        public Builder groupBy(Column<?>... columns) {
            this.groupByColumns = Arrays.asList(columns);
            return this;
        }

        public Builder having(Criterion criterion) {
            this.havingCriterion = criterion;
            return this;
        }

        public SelectJoin build() {
            return new SelectJoin(new ArrayList<>(tablesList), joinsList, onClausesList, whereClause, criterion, dialect, includeDeleted, groupByColumns, havingCriterion, -1, -1, null, true, params.toArray());
        }

        // ─── JoinBuilder interface bridge methods ──────────────────────────────
        // join(Table<?>...), join(Table.Relation), where(Criterion), on(String...)
        // are satisfied by the existing methods above.

        /** {@inheritDoc} */
        @Override
        public <R> Query<List<R>> buildTyped(Class<R> rootType) {
            return build().mapToHierarchy(rootType);
        }
    }
}
