package dev.sweety.transform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for full virtualization: the method body is compiled to
 * a custom VM bytecode and executed by {@link dev.sweety.transform.vm.VMInterpreter}.
 *
 * <p><strong>Performance contract:</strong>
 * VM execution is ~8-15× slower than native JVM bytecode due to reflection-based
 * method dispatch. Only annotate methods that run at most once per player session
 * (startup, auth, license validation). Never annotate per-tick or per-packet methods.
 *
 * <p>Restrictions (method must):
 * <ul>
 *   <li>Have no synchronized blocks</li>
 *   <li>Not use JSR/RET instructions (pre-Java-6 subroutines)</li>
 *   <li>Not override native methods</li>
 * </ul>
 *
 * The annotation is stripped from the output class (RETENTION = CLASS).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Documented
public @interface Virtualize {

    /**
     * If {@code true}, also applies all {@link Transform} transformations to
     * the VM bytecode before encoding it. Maximally opaque but slower to load.
     */
    boolean transform() default true;
}
