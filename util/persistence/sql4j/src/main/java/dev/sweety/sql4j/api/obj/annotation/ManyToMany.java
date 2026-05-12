package dev.sweety.sql4j.api.obj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ManyToMany {
    /** Name of the junction/join table. */
    String joinTable() default "";

    /**
     * When to load the related collection.
     * Defaults to {@link FetchType#LAZY}.
     */
    FetchType fetchType() default FetchType.LAZY;
}
