package dev.sweety.math.pool;

import java.lang.annotation.*;

/**
 * Marks a class whose instances are managed by an object pool.
 * Instances must be obtained via the pool (never {@code new}) and
 * returned via {@link Release}-annotated methods when no longer needed.
 *
 * <pre>{@code
 * @Pooled(pool = PacketBufferAllocator.class)
 * public class PacketBuffer { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Pooled {

    /** The allocator or pool type that manages instances of the annotated class. */
    Class<?> pool() default Void.class;
}
