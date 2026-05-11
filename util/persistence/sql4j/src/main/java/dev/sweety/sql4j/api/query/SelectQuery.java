package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

    CompletableFuture<Page<T>> executePage(SqlConnection con, int page, int size);
    CompletableFuture<Stream<T>> executeStream(SqlConnection con);
    CompletableFuture<List<Row>> executeAggregate(SqlConnection con);
}

