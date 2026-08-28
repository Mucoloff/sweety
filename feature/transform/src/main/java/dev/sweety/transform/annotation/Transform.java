package dev.sweety.transform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class for bytecode transformation.
 *
 * Applied at class level → all non-synthetic, non-native methods are transformed.
 * Applied at method level → only that method.
 *
 * Transformations applied (in order):
 *   1. GOTO normalization
 *   2. Conditional expression mutation
 *   3. Exception-based control flow mutation  ← only if {@link #exceptionFlow()} = true
 *   4. Integer constant encoding
 *   5. String constant encryption
 *
 * The annotation is stripped from the output class (RETENTION = CLASS).
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Transform {

    /**
     * If {@code true}, also applies exception-based control-flow mutation.
     * Slightly heavier — keep false for methods called >500 times/sec.
     */
    boolean exceptionFlow() default false;

    /**
     * If {@code true}, encrypts string constants inside this method/class.
     * Default true — low runtime cost (XOR + array copy).
     */
    boolean strings() default true;

    /**
     * If {@code true}, encodes integer LDC constants as arithmetic expressions.
     * Only for security-sensitive constants; avoid in hot math loops.
     */
    boolean integers() default false;
}
