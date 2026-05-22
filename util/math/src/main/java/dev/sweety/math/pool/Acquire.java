package dev.sweety.math.pool;

import java.lang.annotation.*;

/**
 * Marks a method that transfers ownership of a pooled object to the caller.
 * The caller is responsible for calling the release method when done.
 *
 * <pre>{@code
 * @Acquire
 * PacketBuffer buffer = PacketBufferAllocator.DEFAULT.buffer();
 * try {
 *     // use buffer
 * } finally {
 *     buffer.release(); // caller must release
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Acquire {

    /** Name of the method the caller must invoke to return the object to the pool. */
    String releaseMethod() default "release";
}
