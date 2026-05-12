package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.dialect.Dialect;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A composable SQL predicate used in {@code WHERE}, {@code HAVING}, and {@code DELETE WHERE}
 * clauses.
 *
 * <p>Criteria are obtained from static factory methods on this interface
 * (e.g. {@link #eq}, {@link #isNull}, {@link #like}) or from the per-column DSL
 * generated on mirror classes ({@code UserTable.NAME.eq("Alice")}):
 *
 * <pre>{@code
 * Criterion filter = UserTable.ROLE.eq("admin").and(UserTable.ACTIVE.eq(true));
 * users.select().where(filter).execute(con).join();
 * }</pre>
 *
 * <p>Every {@code Criterion} produces parameterised SQL via {@link #toSql} and
 * binds its values via {@link #bind}. Compound criteria ({@link #and}, {@link #or},
 * {@link #not}) delegate to their children recursively.
 */
public interface Criterion {

    /**
     * Renders this criterion's SQL fragment using the default (no-dialect) quoting.
     * Prefer {@link #toSql(Dialect)} when a dialect is available.
     *
     * @return the SQL predicate string with {@code ?} placeholders
     */
    default String toSql() {
        return toSql(null);
    }

    /**
     * Renders this criterion's SQL fragment using dialect-specific identifier quoting.
     *
     * @param dialect the active SQL dialect (may be {@code null} to skip quoting)
     * @return the SQL predicate string with {@code ?} placeholders
     */
    String toSql(Dialect dialect);

    /**
     * Binds this criterion's parameter values to the given {@link PreparedStatement},
     * starting at {@code startIdx} (1-based JDBC index).
     *
     * @param ps       the statement to bind to
     * @param startIdx the 1-based index of the first {@code ?} placeholder for this criterion
     * @throws SQLException if binding fails
     */
    void bind(PreparedStatement ps, int startIdx) throws SQLException;

    /**
     * Returns the number of {@code ?} placeholders produced by {@link #toSql}.
     *
     * @return the parameter count (≥ 0)
     */
    int countParameters();

    /**
     * Combines this criterion with {@code other} using {@code AND}.
     *
     * @param other the right-hand side criterion
     * @return a new {@code Criterion} representing {@code (this AND other)}
     */
    default Criterion and(Criterion other) {
        return and(this, other);
    }

    /**
     * Combines this criterion with {@code other} using {@code OR}.
     *
     * @param other the right-hand side criterion
     * @return a new {@code Criterion} representing {@code (this OR other)}
     */
    default Criterion or(Criterion other) {
        return or(this, other);
    }

    /**
     * Negates this criterion with {@code NOT}.
     *
     * @return a new {@code Criterion} representing {@code NOT (this)}
     */
    default Criterion not() {
        return not(this);
    }


    // ─── Comparison factories ────────────────────────────────────────────────────

    /** Creates a {@code col = ?} criterion. */
    static Criterion eq(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "=", value);
    }

    /** Creates a {@code col <> ?} criterion. */
    static Criterion ne(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<>", value);
    }

    /** Creates a {@code col > ?} criterion. */
    static Criterion gt(Column<?> col, Object value) {
        return new ComparisonCriterion(col, ">", value);
    }

    /** Creates a {@code col >= ?} criterion. */
    static Criterion ge(Column<?> col, Object value) {
        return new ComparisonCriterion(col, ">=", value);
    }

    /** Creates a {@code col < ?} criterion. */
    static Criterion lt(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<", value);
    }

    /** Creates a {@code col <= ?} criterion. */
    static Criterion le(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<=", value);
    }

    /** Creates a {@code col IS NULL} criterion (no parameters). */
    static Criterion isNull(Column<?> col) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IS NULL"; 
            }
            @Override public void bind(PreparedStatement ps, int startIdx) {}
            @Override public int countParameters() { return 0; }
        };
    }

    /** Creates a {@code col IS NOT NULL} criterion (no parameters). */
    static Criterion isNotNull(Column<?> col) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IS NOT NULL"; 
            }
            @Override public void bind(PreparedStatement ps, int startIdx) {}
            @Override public int countParameters() { return 0; }
        };
    }

    /** Creates a {@code col BETWEEN ? AND ?} criterion. */
    static Criterion between(Column<?> col, Object min, Object max) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " BETWEEN ? AND ?"; 
            }
            @Override public void bind(PreparedStatement ps, int startIdx) throws SQLException {
                ps.setObject(startIdx, min instanceof Enum<?> e ? e.name() : min);
                ps.setObject(startIdx + 1, max instanceof Enum<?> e ? e.name() : max);
            }
            @Override public int countParameters() { return 2; }
        };
    }

    /** Creates a {@code col LIKE ?} criterion. Use {@code %} wildcards in {@code pattern}. */
    static Criterion like(Column<?> col, String pattern) {
        return new ComparisonCriterion(col, "LIKE", pattern);
    }

    /**
     * Creates a {@code col IN (?, ?, …)} criterion.
     * The number of placeholders matches {@code values.size()}.
     */
    static Criterion in(Column<?> col, Collection<?> values) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) {
                String placeholders = values.stream().map(_ -> "?").collect(Collectors.joining(", "));
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IN (" + placeholders + ")";
            }
            @Override public void bind(PreparedStatement ps, int startIdx) throws SQLException {
                int idx = startIdx;
                for (Object v : values) ps.setObject(idx++, v instanceof Enum<?> e ? e.name() : v);
            }
            @Override public int countParameters() { return values.size(); }
        };
    }

    /**
     * Creates a raw SQL criterion with positional {@code ?} parameters.
     * Use sparingly — prefer the typed factories above for type safety.
     *
     * @param sql    the SQL predicate string containing {@code ?} placeholders
     * @param params values to bind, in order
     * @return a raw {@code Criterion}
     */
    static Criterion raw(String sql, Object... params) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) { return sql; }
            @Override public void bind(PreparedStatement ps, int startIdx) throws SQLException {
                int idx = startIdx;
                if (params != null) {
                    for (Object p : params) ps.setObject(idx++, p instanceof Enum<?> e ? e.name() : p);
                }
            }
            @Override public int countParameters() { return params != null ? params.length : 0; }
        };
    }

    // ─── Logical combinators ─────────────────────────────────────────────────────

    /**
     * Combines multiple criteria with {@code AND}.
     *
     * @param criteria two or more criteria to combine
     * @return {@code (c1 AND c2 AND …)}
     */
    static Criterion and(Criterion... criteria) {
        return new LogicalCriterion("AND", criteria);
    }

    /**
     * Combines multiple criteria with {@code OR}.
     *
     * @param criteria two or more criteria to combine
     * @return {@code (c1 OR c2 OR …)}
     */
    static Criterion or(Criterion... criteria) {
        return new LogicalCriterion("OR", criteria);
    }

    /**
     * Negates a criterion with {@code NOT}.
     *
     * @param criterion the criterion to negate
     * @return {@code NOT (criterion)}
     */
    static Criterion not(Criterion criterion) {
        return new Criterion() {
            @Override public String toSql(Dialect dialect) { 
                return "NOT (" + criterion.toSql(dialect) + ")"; 
            }
            @Override public void bind(PreparedStatement ps, int startIdx) throws SQLException { criterion.bind(ps, startIdx); }
            @Override public int countParameters() { return criterion.countParameters(); }
        };
    }

    default Object getPkValue(Table<?> table) {
        return null;
    }

    class ComparisonCriterion implements Criterion {
        private final Column<?> column;
        private final String operator;
        private final Object value;

        public ComparisonCriterion(Column<?> column, String operator, Object value) {
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        public Column<?> column() { return column; }
        public String operator() { return operator; }
        public Object value() { return value; }

        @Override
        public String toSql(Dialect dialect) {
            return (dialect != null ? column.toSql(dialect) : column.name()) + " " + operator + " ?";
        }

        @Override
        public void bind(PreparedStatement ps, int startIdx) throws SQLException {
            ps.setObject(startIdx, value instanceof Enum<?> e ? e.name() : value);
        }

        @Override
        public int countParameters() {
            return 1;
        }

        @Override
        public Object getPkValue(Table<?> table) {
            if (column.table().equals(table) && column.isPrimaryKey() && operator.equals("=")) {
                return value;
            }
            return null;
        }
    }

    class LogicalCriterion implements Criterion {
        private final String operator;
        private final List<Criterion> criteria;

        public LogicalCriterion(String operator, Criterion... criteria) {
            this.operator = operator;
            this.criteria = Arrays.asList(criteria);
        }

        @Override
        public String toSql(Dialect dialect) {
            return "(" + criteria.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(" " + operator + " ")) + ")";
        }

        @Override
        public void bind(PreparedStatement ps, int startIdx) throws SQLException {
            int idx = startIdx;
            for (Criterion c : criteria) {
                c.bind(ps, idx);
                idx += c.countParameters();
            }
        }

        @Override
        public int countParameters() {
            return criteria.stream().mapToInt(Criterion::countParameters).sum();
        }
    }
}
