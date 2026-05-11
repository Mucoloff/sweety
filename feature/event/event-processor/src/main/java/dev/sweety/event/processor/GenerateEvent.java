package dev.sweety.event.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an interface as an event template for code generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateEvent {
    
    /**
     * Custom name for the generated event. If empty, uses the annotated interface name.
     */
    String value() default "";

    /**
     * Whether to generate a Mutable interface and implementation.
     */
    boolean mutable() default true;

    /**
     * Whether to generate an Immutable implementation (factory for read-only view).
     */
    boolean immutable() default true;
    
    /**
     * Suffix for the generated event interface if different from the template.
     * Default behavior: if the template ends in "Template", it's removed. 
     * If not, the template itself acts as the read-only interface.
     */
    String suffix() default "Event";
}
