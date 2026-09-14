package dev.sweety.sql4j.api.obj;

/**
 * Symmetric value transform applied to a single column on every persist and every
 * hydrate. Declared per-column via {@link Column.Info#converter()}.
 *
 * <p>Introduced to support at-rest column encryption (reversible PII) without
 * scattering encrypt/decrypt calls across every service. A column that names a
 * converter has its value transformed at exactly two choke points:
 * <ul>
 *   <li>{@link Column#set(java.sql.PreparedStatement, int, Object)} — the field
 *       value is passed through {@link #toDatabase} right before it is bound to
 *       the {@code INSERT}/{@code UPDATE}/{@code UPSERT} statement;</li>
 *   <li>{@link Column#set(Object, Object)} — the raw column value read from the
 *       {@code ResultSet} is passed through {@link #fromDatabase} before it lands
 *       on the entity field.</li>
 * </ul>
 *
 * <p><b>Not applied to WHERE parameters.</b> A {@link dev.sweety.sql4j.api.query.Criterion}
 * binds its comparison value directly, so callers that look up a converted column
 * (e.g. {@code findByEmail}) must transform the query value themselves before
 * building the criterion. This is deliberate: it keeps the ORM core unaware of the
 * cipher and lets deterministic vs. randomized schemes be chosen per call.
 *
 * <p>Implementations must be null-safe and have a public no-arg constructor
 * ({@link Column} instantiates them reflectively, once, when the table is
 * registered). The default {@link None} is a no-op so every column that does not
 * opt in behaves exactly as before.
 */
public interface ColumnConverter {

    /** Entity field value → value bound to the SQL statement. Must accept/return {@code null}. */
    Object toDatabase(Object value);

    /** Value read from the {@code ResultSet} → value set on the entity field. Must accept/return {@code null}. */
    Object fromDatabase(Object value);

    /** No-op converter; the default so unannotated columns are untouched. */
    final class None implements ColumnConverter {
        @Override public Object toDatabase(Object value) { return value; }
        @Override public Object fromDatabase(Object value) { return value; }
    }
}
