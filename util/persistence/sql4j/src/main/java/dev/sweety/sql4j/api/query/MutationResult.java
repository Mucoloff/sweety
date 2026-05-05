package dev.sweety.sql4j.api.query;

/**
 * Result of a mutating query (INSERT / UPSERT) that returns both the number of affected rows
 * (or the generated ID) and the entity instance.
 *
 * @param value The integer result (typically the auto-generated primary key or number of rows affected).
 * @param entity The entity instance associated with the mutation.
 */
public record MutationResult<T>(int value, T entity) {
}
