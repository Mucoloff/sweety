package dev.sweety.sql4j.api.obj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToMany {
    /** The foreign-key column in the child table that references this entity's PK. */
    String mappedBy();

    /**
     * When to load the child collection.
     * Defaults to {@link FetchType#LAZY} — the relation is only included when
     * explicitly requested via {@code .fetch(TABLE.THIS_REL)}.
     * Set to {@link FetchType#EAGER} to auto-join on every {@code select()}.
     */
    FetchType fetchType() default FetchType.LAZY;
}
