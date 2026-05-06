package dev.sweety.sql4j.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for custom SQL queries in repository interfaces.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {
    /**
     * @return The raw SQL query. Use ? for parameters.
     */
    String value();
    
    /**
     * @return Whether this query is a native/raw query that should bypass DSL parsing.
     */
    boolean nativeQuery() default true;
}
