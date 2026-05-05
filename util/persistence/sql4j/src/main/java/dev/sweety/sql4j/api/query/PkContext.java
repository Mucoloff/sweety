package dev.sweety.sql4j.api.query;

import dev.sweety.sql4j.api.obj.Column;
import dev.sweety.sql4j.api.obj.Table;
import dev.sweety.sql4j.impl.Repository;

import java.util.List;
import java.util.Objects;

/**
 * A fluent context for operations targeting an entity by its primary key.
 *
 * @param <T> The entity type
 */
public final class PkContext<T> {

    private final Repository<T> repository;
    private final Object[] values;

    public PkContext(Repository<T> repository, Object... values) {
        this.repository = Objects.requireNonNull(repository, "repository is null");
        this.values = Objects.requireNonNull(values, "values is null");
        
        List<Column<?>> pks = repository.table().primaryKeys();
        if (values.length != pks.size()) {
            throw new IllegalArgumentException(
                    "Table " + repository.table().name() + " expects " + pks.size() + 
                    " primary key values, but " + values.length + " were provided.");
        }
    }

    private Criterion buildCriterion() {
        List<Column<?>> pks = repository.table().primaryKeys();
        Criterion combined = null;
        for (int i = 0; i < pks.size(); i++) {
            @SuppressWarnings("unchecked")
            Column<Object> col = (Column<Object>) pks.get(i);
            Criterion current = col.eq(values[i]);
            combined = (combined == null) ? current : combined.and(current);
        }
        return combined;
    }

    /**
     * Finds the entity by its primary key.
     * 
     * @return A query that returns the entity or null if not found.
     */
    public Query<T> find() {
        return repository.wrapWithCache(values, () -> repository.select().where(buildCriterion()).limit(1)
                .extractObjects(list -> list.isEmpty() ? null : list.get(0)));
    }

    /**
     * Deletes the entity by its primary key.
     * 
     * @return A query that returns the number of affected rows (usually 1 or 0).
     */
    public Query<Integer> delete() {
        return repository.deleteWhere(buildCriterion());
    }

    /**
     * Replaces the entity in the database. 
     * Note: This is an alias for repository.update(entity) but ensures the PK matches this context.
     * 
     * @param entity The entity instance to update
     * @return A query that returns the number of affected rows.
     */
    public Query<Integer> update(T entity) {
        // Verification: ensure the entity's PK matches the context values
        List<Column<?>> pks = repository.table().primaryKeys();
        for (int i = 0; i < pks.size(); i++) {
            Object entityVal = pks.get(i).get(entity);
            if (!Objects.equals(entityVal, values[i])) {
                throw new IllegalArgumentException("Entity primary key does not match PK context value at index " + i);
            }
        }
        return repository.update(entity);
    }
}
