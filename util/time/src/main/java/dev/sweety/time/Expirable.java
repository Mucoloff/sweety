package dev.sweety.time;

public interface Expirable {

    /**
     * Absolute deadline in nanoseconds, as returned by {@link System#nanoTime()}.
     * A value of {@code 0} means no expiry.
     */
    long expireAt();

    default boolean hasExpiry() {
        return this.expireAt() > 0L;
    }

    default long expiry() {
        return hasExpiry() ? this.expireAt() : 0L;
    }

    default boolean expired() {
        final long expiry = expiry();
        return expiry > 0L && expiry < now();
    }

    /** Remaining nanoseconds until expiry, or {@code 0} if no expiry set. */
    default long expiryTime() {
        final long expiry = expiry();
        if (expiry <= 0) return 0L;
        return expiry - now();
    }

    /** Returns {@link System#nanoTime()} — monotonic, never goes backwards. */
    default long now() {
        return System.nanoTime();
    }
}