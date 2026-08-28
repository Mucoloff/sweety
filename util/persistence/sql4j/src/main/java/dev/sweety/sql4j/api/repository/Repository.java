package dev.sweety.sql4j.api.repository;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.BatchQuery;
import dev.sweety.sql4j.api.query.ConditionalDeleteQuery;
import dev.sweety.sql4j.api.query.ConditionalUpdateQuery;
import dev.sweety.sql4j.api.query.Criterion;
import dev.sweety.sql4j.api.query.DeleteQuery;
import dev.sweety.sql4j.api.query.InsertQuery;
import dev.sweety.sql4j.api.query.Query;
import dev.sweety.sql4j.api.query.UpdateQuery;
import dev.sweety.sql4j.api.query.UpsertQuery;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Full read-write repository interface for SQL4J-managed entities.
 *
 * <p>Extends {@link ReadOnlyRepository} with all mutating operations (INSERT, UPDATE, DELETE,
 * UPSERT, batch operations, and DDL). Pass a {@link ReadOnlyRepository} when you want the
 * compiler to prevent accidental mutation.
 *
 * @param <Entity> the entity type managed by this repository
 */
public interface Repository<Entity> extends ReadOnlyRepository<Entity> {

    // ReadOnlyRepository methods are inherited; see that interface for Javadoc.

    /**
     * Creates an INSERT query for a single entity.
     */
    @NotNull InsertQuery<Entity> insert(@NotNull Entity entity);

    /**
     * Creates a BATCH INSERT query for a collection of entities.
     */
    @NotNull BatchQuery<Entity> insertBatch(@NotNull Collection<Entity> entities);

    /**
     * Creates an UPSERT (INSERT OR UPDATE) query for a single entity.
     */
    @NotNull UpsertQuery<Entity> upsert(@NotNull Entity entity);

    /**
     * Creates an UPDATE query for a single entity matched by its primary key.
     */
    @NotNull UpdateQuery<Entity> update(@NotNull Entity entity);

    /**
     * Creates a BATCH UPDATE query for a collection of entities.
     */
    @NotNull BatchQuery<Entity> updateBatch(@NotNull Collection<Entity> entities);

    /**
     * Creates a DELETE query for a single entity matched by its primary key.
     */
    @NotNull DeleteQuery<Entity> delete(@NotNull Entity entity);

    /**
     * Starts a conditional DELETE WHERE query.
     */
    @NotNull ConditionalDeleteQuery<Entity> deleteWhere();

    /**
     * Creates a conditional DELETE WHERE query with an initial criterion.
     */
    @NotNull ConditionalDeleteQuery<Entity> deleteWhere(@NotNull Criterion criterion);

    /**
     * Starts a conditional UPDATE WHERE query.
     */
    @NotNull ConditionalUpdateQuery<Entity> updateWhere();

    /**
     * Starts a conditional UPDATE WHERE query with an initial criterion.
     */
    @NotNull ConditionalUpdateQuery<Entity> updateWhere(@NotNull Criterion criterion);

    /**
     * Creates a CREATE TABLE query (with IF NOT EXISTS).
     *
     * <p>The returned {@link Query Query&lt;Void&gt;} also implements
     * {@link dev.sweety.sql4j.api.query.schema.CreateTableQuery} and can be cast if needed.
     */
    @NotNull Query<Void> createTable();

    /**
     * Creates a DROP TABLE query.
     *
     * <p>The returned {@link Query Query&lt;Void&gt;} also implements
     * {@link dev.sweety.sql4j.api.query.schema.DropTableQuery} and can be cast if needed.
     */
    @NotNull Query<Void> dropTable();
}
