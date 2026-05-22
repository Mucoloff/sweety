package dev.sweety.math.pool;

import java.lang.annotation.*;

/**
 * Marks that a method borrows a pooled object without taking ownership.
 * The method will not release the object; the original owner retains responsibility.
 *
 * <p>On a <b>method</b>: the method uses the object internally but does not release it.
 * <p>On a <b>parameter</b>: the callee borrows the argument — the caller retains ownership.
 *
 * <pre>{@code
 * // callee borrows buf — caller still owns it and must release it
 * encode(@Borrows PacketBuffer buf, ChannelHandlerContext ctx) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface Borrows {
}
