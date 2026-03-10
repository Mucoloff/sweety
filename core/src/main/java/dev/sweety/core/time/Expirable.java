package dev.sweety.core.time;

public interface Expirable {
    long getExpireAt();

    default boolean hasExpiry() {
        return this.getExpireAt() != 0L;
    }

    default long getExpiry() {
        return hasExpiry() ? this.getExpireAt() : -1L;
    }

    default boolean hasExpired() {
        long expiry = getExpiry();
        return expiry != -1 && expiry < now();
    }

    default long getExpiryTime() {
        long expiry = getExpiry();
        if (expiry == -1) return -1;
        return expiry - now();
    }

    default long now() {
        return System.currentTimeMillis();
    }
}