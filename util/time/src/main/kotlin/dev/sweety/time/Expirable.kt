package dev.sweety.time

interface Expirable {

    /**
     * Absolute deadline in nanoseconds, as returned by [System.nanoTime].
     * A value of `0` means no expiry.
     */
    fun expireAt(): Long

    fun hasExpiry(): Boolean = expireAt() > 0L

    fun expiry(): Long = if (hasExpiry()) expireAt() else 0L

    fun expired(): Boolean {
        val expiry = expiry()
        return expiry > 0L && expiry < now()
    }

    /** Remaining nanoseconds until expiry, or `0` if no expiry set. */
    fun expiryTime(): Long {
        val expiry = expiry()
        if (expiry <= 0) return 0L
        return expiry - now()
    }

    /** Returns [System.nanoTime] — monotonic, never goes backwards. */
    fun now(): Long = System.nanoTime()
}
