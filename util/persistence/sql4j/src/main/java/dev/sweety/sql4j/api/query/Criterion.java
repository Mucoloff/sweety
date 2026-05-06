package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public interface Criterion {
    default String toSql() {
        return toSql(null);
    }
    String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect);
    void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException;
    int countParameters();

    default Criterion and(Criterion other) {
        return and(this, other);
    }

    default Criterion or(Criterion other) {
        return or(this, other);
    }

    default Criterion not() {
        return not(this);
    }


    static Criterion eq(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "=", value);
    }

    static Criterion ne(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<>", value);
    }

    static Criterion gt(Column<?> col, Object value) {
        return new ComparisonCriterion(col, ">", value);
    }

    static Criterion ge(Column<?> col, Object value) {
        return new ComparisonCriterion(col, ">=", value);
    }

    static Criterion lt(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<", value);
    }

    static Criterion le(Column<?> col, Object value) {
        return new ComparisonCriterion(col, "<=", value);
    }

    static Criterion isNull(Column<?> col) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IS NULL"; 
            }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) {}
            @Override public int countParameters() { return 0; }
        };
    }

    static Criterion isNotNull(Column<?> col) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IS NOT NULL"; 
            }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) {}
            @Override public int countParameters() { return 0; }
        };
    }

    static Criterion between(Column<?> col, Object min, Object max) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) { 
                return (dialect != null ? col.toSql(dialect) : col.name()) + " BETWEEN ? AND ?"; 
            }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
                ps.setObject(startIdx, min);
                ps.setObject(startIdx + 1, max);
            }
            @Override public int countParameters() { return 2; }
        };
    }

    static Criterion like(Column<?> col, String pattern) {
        return new ComparisonCriterion(col, "LIKE", pattern);
    }

    static Criterion in(Column<?> col, java.util.Collection<?> values) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
                String placeholders = values.stream().map(_ -> "?").collect(Collectors.joining(", "));
                return (dialect != null ? col.toSql(dialect) : col.name()) + " IN (" + placeholders + ")";
            }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
                int idx = startIdx;
                for (Object v : values) ps.setObject(idx++, v);
            }
            @Override public int countParameters() { return values.size(); }
        };
    }

    static Criterion raw(String sql, Object... params) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) { return sql; }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
                int idx = startIdx;
                if (params != null) {
                    for (Object p : params) ps.setObject(idx++, p);
                }
            }
            @Override public int countParameters() { return params != null ? params.length : 0; }
        };
    }

    static Criterion and(Criterion... criteria) {
        return new LogicalCriterion("AND", criteria);
    }

    static Criterion or(Criterion... criteria) {
        return new LogicalCriterion("OR", criteria);
    }

    static Criterion not(Criterion criterion) {
        return new Criterion() {
            @Override public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) { 
                return "NOT (" + criterion.toSql(dialect) + ")"; 
            }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException { criterion.bind(ps, startIdx); }
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
        public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
            return (dialect != null ? column.toSql(dialect) : column.name()) + " " + operator + " ?";
        }

        @Override
        public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
            ps.setObject(startIdx, value);
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
        public String toSql(dev.sweety.sql4j.api.connection.dialect.Dialect dialect) {
            return "(" + criteria.stream().map(c -> c.toSql(dialect)).collect(Collectors.joining(" " + operator + " ")) + ")";
        }

        @Override
        public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
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
