package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;

public interface ConditionalUpdateQuery<T> extends UpdateQuery<T> {
    <V> ConditionalUpdateQuery<T> set(Column<V> column, V value);
    ConditionalUpdateQuery<T> where(Criterion criterion);
}

