package dev.sweety.sql4j.api.obj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field (typically boolean or timestamp) as a soft-delete indicator.
 * Queries will automatically filter by this field unless specified otherwise.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SoftDelete {
    String columnName() default "deleted";
}
