package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public interface Criterion {
    String toSql();
    void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException;
    int countParameters();

    static Criterion eq(Column col, Object value) {
        return new ComparisonCriterion(col, "=", value);
    }

    static Criterion ne(Column col, Object value) {
        return new ComparisonCriterion(col, "<>", value);
    }

    static Criterion gt(Column col, Object value) {
        return new ComparisonCriterion(col, ">", value);
    }

    static Criterion ge(Column col, Object value) {
        return new ComparisonCriterion(col, ">=", value);
    }

    static Criterion lt(Column col, Object value) {
        return new ComparisonCriterion(col, "<", value);
    }

    static Criterion le(Column col, Object value) {
        return new ComparisonCriterion(col, "<=", value);
    }

    static Criterion like(Column col, String pattern) {
        return new ComparisonCriterion(col, "LIKE", pattern);
    }

    static Criterion and(Criterion... criteria) {
        return new LogicalCriterion("AND", criteria);
    }

    static Criterion or(Criterion... criteria) {
        return new LogicalCriterion("OR", criteria);
    }

    static Criterion not(Criterion criterion) {
        return new Criterion() {
            @Override public String toSql() { return "NOT (" + criterion.toSql() + ")"; }
            @Override public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException { criterion.bind(ps, startIdx); }
            @Override public int countParameters() { return criterion.countParameters(); }
        };
    }

    class ComparisonCriterion implements Criterion {
        private final Column column;
        private final String operator;
        private final Object value;

        public ComparisonCriterion(Column column, String operator, Object value) {
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        @Override
        public String toSql() {
            return column.name() + " " + operator + " ?";
        }

        @Override
        public void bind(java.sql.PreparedStatement ps, int startIdx) throws java.sql.SQLException {
            ps.setObject(startIdx, value);
        }

        @Override
        public int countParameters() {
            return 1;
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
        public String toSql() {
            return "(" + criteria.stream().map(Criterion::toSql).collect(Collectors.joining(" " + operator + " ")) + ")";
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
