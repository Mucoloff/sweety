package dev.sweety.event.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an interface as an event template for code generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GenerateEvent {

    /**
     * Custom name for the generated event. If empty, uses the annotated interface name.
     */
    String value() default "";

    EventType type() default EventType.BOTH;

    enum EventType {
        /**
         * Whether to generate a Mutable interface and implementation.
         */
        MUTABLE,
        /**
         * Whether to generate an Immutable implementation (factory for read-only view).
         */
        IMMUTABLE,
        BOTH;

        public boolean mutable() {
            return this == MUTABLE || this == BOTH;
        }

        public boolean immutable() {
            return this == IMMUTABLE || this == BOTH;
        }
    }
}
