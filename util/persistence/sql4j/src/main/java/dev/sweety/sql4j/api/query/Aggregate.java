package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;

public final class Aggregate {

    public enum Type {
        COUNT, SUM, AVG, MIN, MAX
    }

    public static AggregateColumn count(Column<?> column) {
        return new AggregateColumn(Type.COUNT, column);
    }

    public static AggregateColumn sum(Column<?> column) {
        return new AggregateColumn(Type.SUM, column);
    }

    public static AggregateColumn avg(Column<?> column) {
        return new AggregateColumn(Type.AVG, column);
    }

    public static AggregateColumn min(Column<?> column) {
        return new AggregateColumn(Type.MIN, column);
    }

    public static AggregateColumn max(Column<?> column) {
        return new AggregateColumn(Type.MAX, column);
    }

    public static class AggregateColumn extends Column<Object> {
        private final Type aggregateType;
        private final Column<?> wrapped;

        private AggregateColumn(Type type, Column<?> wrapped) {
            super(wrapped.table(), type.name() + "(" + wrapped.name() + ")");
            this.aggregateType = type;
            this.wrapped = wrapped;
        }

        public Type aggregateType() { return aggregateType; }
        public Column<?> wrapped() { return wrapped; }

        @Override
        public String name() {
            return aggregateType.name() + "(" + wrapped.table().name() + "." + wrapped.name() + ")";
        }
        
        public String alias() {
            return aggregateType.name().toLowerCase() + "_" + wrapped.name();
        }

        @Override
        public Class<?> type() {
            return switch (aggregateType) {
                case COUNT -> Long.class;
                case AVG -> Double.class;
                case SUM -> wrapped.type();
                default -> wrapped.type();
            };
        }
    }
}
