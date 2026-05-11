package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;

import java.util.List;

/**
 * Public API for building JOIN queries across multiple tables.
 *
 * <p>Obtain an instance via {@link dev.sweety.sql4j.api.repository.Repository#joinBuilder()}.
 *
 * <p>The concrete implementation is {@code dev.sweety.sql4j.impl.query.SelectJoin.Builder}.
 *
 * @see dev.sweety.sql4j.api.repository.Repository#joinBuilder()
 */
public interface JoinBuilder {

    /**
     * Adds tables to the JOIN. Called automatically from the repository entry point
     * but also available for multi-table chaining.
     */
    JoinBuilder join(Table<?>... tables);

    /**
     * Joins via a declared {@link Table.Relation}; FK/PK {@code ON} clauses are
     * inferred automatically.
     */
    JoinBuilder join(Table.Relation relation);

    /** Restricts the JOIN result with a WHERE criterion. */
    JoinBuilder where(Criterion criterion);

    /**
     * Adds an explicit {@code ON} clause string (e.g. {@code "a.id = b.fk"}).
     * Use when automatic FK discovery is not sufficient.
     */
    JoinBuilder on(String... clauses);

    /** Builds and returns a {@link Query} that yields raw {@link Row}s. */
    Query<List<Row>> build();

    /**
     * Builds a typed result query mapping rows to entity instances of {@code rootType}.
     *
     * @param <R>      the root entity type
     * @param rootType the root entity class
     */
    <R> Query<List<R>> buildTyped(Class<R> rootType);
}
