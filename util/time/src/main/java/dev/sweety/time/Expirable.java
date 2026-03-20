package dev.sweety.time;

public interface Expirable {

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

    default long expiryTime() {
        final long expiry = expiry();
        if (expiry <= 0) return 0L;
        return expiry - now();
    }

    default long now() {
        return System.currentTimeMillis();
    }
}