package dev.sweety.time;

/**
 * Interface for objects that have an absolute or relative expiration time.
 */
public interface Expirable {

    /**
     * Absolute deadline in nanoseconds, as returned by {@link System#nanoTime()}.
     * A value of {@code 0} means no expiry.
     */
    long expireAt();

    default boolean hasExpiry() {
        return expireAt() > 0L;
    }

    default long expiry() {
        return hasExpiry() ? expireAt() : 0L;
    }

    default boolean expired() {
        long exp = expiry();
        return exp > 0L && exp < now();
    }

    /**
     * Remaining nanoseconds until expiry, or {@code 0} if no expiry set.
     */
    default long expiryTime() {
        long exp = expiry();
        if (exp <= 0L) return 0L;
        return exp - now();
    }

    /**
     * Returns {@link System#nanoTime()} — monotonic, never goes backwards.
     */
    default long now() {
        return System.nanoTime();
    }
}
