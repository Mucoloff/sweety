package dev.sweety.sql4j.api.repository;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.*;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only projection of a SQL4J {@link Repository}.
 *
 * <p>Exposes only SELECT operations. Use this type in methods or services that must not
 * mutate data, allowing the compiler to enforce the constraint rather than relying on
 * runtime checks or documentation.
 *
 * <p>Obtain an instance by upcasting any {@link Repository} to {@code ReadOnlyRepository}.
 *
 * @param <Entity> the entity type managed by this repository
 */
public interface ReadOnlyRepository<Entity> {

    /** @return the table descriptor for this repository */
    @NotNull Table<Entity> table();

    /**
     * Returns a SELECT query that fetches all columns as entity instances.
     */
    @NotNull SelectQuery<Entity> select();

    /**
     * Returns a SELECT query fetching only the specified columns as entity instances.
     * Unspecified fields are left at their zero / {@code null} defaults.
     */
    @NotNull SelectQuery<Entity> select(@NotNull Column<?>... columns);

    /**
     * Returns a SELECT query that returns raw {@link dev.sweety.sql4j.api.obj.Row}s
     * (all columns, no entity instantiation).
     */
    @NotNull SelectRawQuery selectRawAll();

    /**
     * Returns a SELECT query that returns raw rows with only the given columns.
     */
    @NotNull SelectRawQuery selectRaw(@NotNull Column<?>... columns);

    /**
     * Returns a fluent context scoped to a single entity identified by its primary key.
     */
    @NotNull PkContext<Entity> pk(@NotNull Object... values);

    /**
     * Returns a JOIN builder pre-seeded with this repository's table.
     */
    @NotNull JoinBuilder joinBuilder();

    /**
     * Wraps a query supplier with an entity-cache look-up: returns the cached entity if present,
     * otherwise delegates to the supplier and caches the result.
     */
    @NotNull <T> Query<T> wrapWithCache(@NotNull Object pkValue,
                                        @NotNull java.util.function.Supplier<Query<T>> querySupplier);
}
