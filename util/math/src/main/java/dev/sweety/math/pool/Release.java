package dev.sweety.math.pool;

import java.lang.annotation.*;

/**
 * Marks a method that returns a pooled object to its pool, invalidating the reference.
 * After this call the caller must not read from or write to the object.
 *
 * <pre>{@code
 * buf.write(...);
 * buf.release(); // @Release — do not use buf after this line
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Release {
}
