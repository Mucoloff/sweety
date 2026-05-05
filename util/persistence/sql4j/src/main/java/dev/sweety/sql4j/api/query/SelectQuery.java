package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;

import java.util.List;

public non-sealed interface SelectQuery<T> extends Query<List<T>> {
    SelectQuery<T> where(Criterion criterion);
    SelectQuery<T> select(Column<?>... columns);

    SelectQuery<T> limit(int limit);
    SelectQuery<T> offset(int offset);
    SelectQuery<T> orderBy(String column, boolean ascending);
    SelectQuery<T> fetch(Table.Relation... relations);
    SelectQuery<T> withDeleted();
    SelectQuery<T> groupBy(Column<?>... columns);
    SelectQuery<T> having(Criterion criterion);

    java.util.concurrent.CompletableFuture<Page<T>> executePage(dev.sweety.sql4j.api.connection.SqlConnection con, int page, int size);
    java.util.concurrent.CompletableFuture<java.util.stream.Stream<T>> executeStream(dev.sweety.sql4j.api.connection.SqlConnection con);
    java.util.concurrent.CompletableFuture<java.util.List<dev.sweety.sql4j.api.obj.Row>> executeAggregate(dev.sweety.sql4j.api.connection.SqlConnection con);
}

