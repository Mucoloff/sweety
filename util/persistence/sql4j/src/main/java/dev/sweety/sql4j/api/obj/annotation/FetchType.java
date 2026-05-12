package dev.sweety.sql4j.api.obj.annotation;

/**
 * Controls when a relational collection is loaded relative to its owning entity.
 *
 * <p>Use on {@link OneToMany#fetchType()} and {@link ManyToMany#fetchType()}:
 *
 * <pre>{@code
 * @OneToMany(mappedBy = "order_id", fetchType = FetchType.EAGER)
 * private List<OrderLine> lines;
 * }</pre>
 *
 * <h3>LAZY (default)</h3>
 * The relation is <em>never</em> loaded automatically. Callers must explicitly
 * trigger a join via {@code repository.select().fetch(TABLE.LINES_REL).execute(con)}.
 * This prevents unintended N+1 queries and is the recommended setting for most cases.
 *
 * <h3>EAGER</h3>
 * The relation is automatically included whenever {@code repository.select()} is called,
 * by injecting the relation into the query's {@code fetch()} list before execution.
 * Use only for small, always-needed child collections; it can degrade performance for
 * entities with large or rarely-needed relations.
 */
public enum FetchType {
    /**
     * Relations are not loaded unless explicitly requested via {@code .fetch(REL)}.
     * This is the default and prevents accidental N+1 queries.
     */
    LAZY,

    /**
     * The relation is automatically joined on every {@code select()} call.
     * Equivalent to always chaining {@code .fetch(TABLE.THIS_REL)} before execution.
     */
    EAGER
}
