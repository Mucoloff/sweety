package dev.sweety.math.soa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record or value class for Structure-of-Arrays (SoA) code generation.
 * Generates an entity list backed by parallel FastUtil primitive lists for zero heap overhead.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SoA {
    /** Custom generated list class name. If empty, defaults to <RecordName>List. */
    String listName() default "";
}
