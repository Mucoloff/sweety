package dev.sweety.sql4j.api.repository;

import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.api.query.*;
import dev.sweety.sql4j.impl.query.entity.DeleteWhere;
import dev.sweety.sql4j.impl.query.entity.UpdateWhere;

import java.util.Collection;

/**
 * Base interface for all repositories.
 * @param <Entity> The entity type managed by this repository.
 */
public interface Repository<Entity> {

    /**
     * @return The table descriptor for this repository.
     */
    Table<Entity> table();

    /**
     * Starts a SELECT query.
     */
    SelectQuery<Entity> select();

    /**
     * Creates an INSERT query for a single entity.
     */
    InsertQuery<Entity> insert(Entity entity);

    /**
     * Creates a BATCH INSERT query for a collection of entities.
     */
    BatchQuery<Entity> insertBatch(Collection<Entity> entities);

    /**
     * Creates an UPSERT query (INSERT OR UPDATE) for a single entity.
     */
    UpsertQuery<Entity> upsert(Entity entity);

    /**
     * Creates an UPDATE query for a single entity.
     */
    UpdateQuery<Entity> update(Entity entity);

    /**
     * Creates a DELETE query for a single entity.
     */
    DeleteQuery<Entity> delete(Entity entity);

    /**
     * Starts a DELETE WHERE query.
     */
    ConditionalDeleteQuery<Entity> deleteWhere();

    /**
     * Creates a DELETE WHERE query with a starting criterion.
     */
    ConditionalDeleteQuery<Entity> deleteWhere(Criterion criterion);

    /**
     * Creates a PK context for fluent operations by primary key.
     */
    dev.sweety.sql4j.api.query.PkContext<Entity> pk(Object... values);

    /**
     * Creates an UPDATE query for a collection of entities.
     */
    BatchQuery<Entity> updateBatch(Collection<Entity> entities);

    /**
     * Starts a conditional UPDATE query.
     */
    dev.sweety.sql4j.api.query.ConditionalUpdateQuery<Entity> updateWhere();

    /**
     * Starts a conditional UPDATE query with a starting criterion.
     */
    dev.sweety.sql4j.api.query.ConditionalUpdateQuery<Entity> updateWhere(Criterion criterion);

    /**
     * Starts a join query builder.
     */
    dev.sweety.sql4j.impl.query.SelectJoin.Builder joinBuilder();

    /**
     * Selects specific columns and returns entity instances.
     */
    SelectQuery<Entity> select(dev.sweety.sql4j.api.obj.Column<?>... columns);

    /**
     * Selects all columns and returns raw rows.
     */
    SelectRawQuery selectRawAll();

    /**
     * Selects specific columns and returns raw rows.
     */
    SelectRawQuery selectRaw(dev.sweety.sql4j.api.obj.Column<?>... columns);

    /**
     * Creates a CREATE TABLE query.
     */
    dev.sweety.sql4j.impl.query.table.CreateTable createTable();

    /**
     * Creates a DROP TABLE query.
     */
    dev.sweety.sql4j.impl.query.table.DropTable dropTable();

    /**
     * Wraps a query with cache lookup/storage.
     */
    <T> Query<T> wrapWithCache(Object pkValue, java.util.function.Supplier<Query<T>> querySupplier);
}
