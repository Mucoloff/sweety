package dev.sweety.sql4j.api.query;

public non-sealed interface DeleteQuery<T> extends Query<Integer> {
    DeleteQuery<T> hardDelete();
    DeleteQuery<T> softDelete();
}
