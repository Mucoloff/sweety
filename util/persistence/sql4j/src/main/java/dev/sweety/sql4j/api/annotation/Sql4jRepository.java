package dev.sweety.sql4j.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an interface as a custom SQL4J repository.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sql4jRepository {
    /**
     * @return The entity class managed by this repository.
     */
    Class<?> entity();
}
