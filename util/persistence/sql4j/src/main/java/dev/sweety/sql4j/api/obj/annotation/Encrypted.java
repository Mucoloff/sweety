package dev.sweety.sql4j.api.obj.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a sensitive field (PII / credentials / sensitive chat logs) for envelope encryption
 * using a per-user Data Encryption Key (DEK).
 *
 * <p>Enables instant GDPR Right-to-be-Forgotten compliance via <b>Crypto-Shredding</b>:
 * deleting the user's DEK renders all ciphertext unreadable across historical cold backups,
 * archives, and replication nodes without modifying backup snapshots.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {
}
