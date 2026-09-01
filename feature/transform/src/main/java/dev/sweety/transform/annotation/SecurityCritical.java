package dev.sweety.transform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks security-critical methods (such as payload decryptors, license validators, anti-tamper hooks)
 * to receive high-strength anti-reverse engineering protections:
 * <ul>
 *   <li>Virtualization via the custom VMInterpreter</li>
 *   <li>Opaque Predicate injection (bogus control flow & dead branches)</li>
 *   <li>Anti-Tamper & Anti-Dump stack integrity checks</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SecurityCritical {
}
