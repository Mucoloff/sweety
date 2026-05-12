package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.connection.SqlConnection;
import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Row;
import dev.sweety.sql4j.api.obj.Table;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * A fluent, immutable SELECT query builder for entity-mapped results.
 *
 * <p>Instances are obtained from {@link dev.sweety.sql4j.api.repository.Repository#select()}.
 * Every method returns a new, independent copy of the query, so a prototype can be safely
 * reused and customised per-call:
 *
 * <pre>{@code
 * SelectQuery<User> base = users.select();
 * List<User> admins = base.where(UserTable.ROLE.eq("admin")).execute(con).join();
 * List<User> all    = base.execute(con).join();           // unchanged prototype
 * }</pre>
 *
 * @param <T> the entity type returned by this query
 */
public non-sealed interface SelectQuery<T> extends Query<List<T>> {

    /**
     * Adds a {@code WHERE} predicate. Multiple calls are combined with {@code AND}.
     *
     * @param criterion the filter criterion (built via table-column DSL, e.g.
     *                  {@code UserTable.NAME.eq("Alice")})
     * @return a new query with the criterion appended
     */
    SelectQuery<T> where(Criterion criterion);

    /**
     * Restricts the projected columns to the given set.
     * By default all columns are selected.
     *
     * @param columns columns to include in the {@code SELECT} clause
     * @return a new query projecting only the specified columns
     */
    SelectQuery<T> select(Column<?>... columns);

    /**
     * Sets the {@code LIMIT} clause.
     *
     * @param limit maximum number of rows to return; must be positive
     * @return a new query with the limit applied
     */
    SelectQuery<T> limit(int limit);

    /**
     * Sets the {@code OFFSET} clause (skip the first {@code offset} rows).
     *
     * @param offset number of rows to skip; must be non-negative
     * @return a new query with the offset applied
     */
    SelectQuery<T> offset(int offset);

    /**
     * Appends an {@code ORDER BY} clause.
     *
     * @param column    the column name to sort by
     * @param ascending {@code true} for {@code ASC}, {@code false} for {@code DESC}
     * @return a new query with the ordering applied
     */
    SelectQuery<T> orderBy(String column, boolean ascending);

    /**
     * Eagerly fetches the given relations via a single JOIN query, preventing N+1 selects.
     * The relations must be declared on this entity's table (generated as static {@code *_REL}
     * constants on the mirror class, e.g. {@code PostTable.COMMENTS_REL}).
     *
     * @param relations one or more relation descriptors to join
     * @return a new query that includes the joined child entities
     */
    SelectQuery<T> fetch(Table.Relation... relations);

    /**
     * Includes soft-deleted rows (those whose soft-delete column is set to the "deleted" value).
     * By default, soft-deleted rows are excluded from all SELECT results.
     *
     * @return a new query that returns all rows regardless of their soft-delete status
     */
    SelectQuery<T> withDeleted();

    /**
     * Appends a {@code GROUP BY} clause.
     *
     * @param columns columns to group by
     * @return a new query with grouping applied
     */
    SelectQuery<T> groupBy(Column<?>... columns);

    /**
     * Appends a {@code HAVING} clause (requires a prior {@link #groupBy}).
     *
     * @param criterion the post-aggregation filter criterion
     * @return a new query with the HAVING clause applied
     */
    SelectQuery<T> having(Criterion criterion);

    /**
     * Executes this query and returns one page of results.
     *
     * @param con  the SQL4J connection
     * @param page zero-based page index
     * @param size page size (number of rows per page)
     * @return a {@link CompletableFuture} completing with a {@link Page} that includes
     *         the result rows and total row count metadata
     */
    CompletableFuture<Page<T>> executePage(SqlConnection con, int page, int size);

    /**
     * Executes this query and streams results lazily.
     * The stream must be consumed and closed within the async callback; the underlying
     * {@link java.sql.ResultSet} is closed when the stream is closed.
     *
     * @param con the SQL4J connection
     * @return a {@link CompletableFuture} completing with a lazy {@link Stream} of entities
     */
    CompletableFuture<Stream<T>> executeStream(SqlConnection con);

    /**
     * Executes this query and returns raw {@link Row} objects, typically used with
     * aggregation functions ({@code COUNT}, {@code SUM}, etc.) applied via
     * {@link #groupBy}/{@link #having}.
     *
     * @param con the SQL4J connection
     * @return a {@link CompletableFuture} completing with a list of raw rows
     */
    CompletableFuture<List<Row>> executeAggregate(SqlConnection con);
}
