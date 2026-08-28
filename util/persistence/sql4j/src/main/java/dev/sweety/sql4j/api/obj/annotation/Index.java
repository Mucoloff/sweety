package dev.sweety.sql4j.api.obj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a column or an entity table to be indexed.
 * When placed on a FIELD, it creates a single-column index.
 * When placed on a CLASS (TYPE), it can define a multi-column composite index via {@link #columns()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
@Repeatable(Indexes.class)
public @interface Index {
    String name() default "";
    String[] columns() default {};
    boolean unique() default false;
}
