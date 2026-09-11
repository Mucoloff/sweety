package dev.sweety.versioning.server.data

import dev.sweety.time.Expirable
import dev.sweety.versioning.version.Version
import dev.sweety.versioning.version.channel.Channel

open class ForcedUpdate(
    open val channel: Channel,
    open val prev: Version?,
    open val target: Version,
    open val expireAt: Long
) : Expirable {
    fun channel(): Channel = channel
    fun prev(): Version? = prev
    fun target(): Version = target
    override fun expireAt(): Long = expireAt

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ForcedUpdate) return false
        return channel == other.channel && prev == other.prev && target == other.target && expireAt == other.expireAt
    }

    override fun hashCode(): Int {
        var result = channel.hashCode()
        result = 31 * result + (prev?.hashCode() ?: 0)
        result = 31 * result + target.hashCode()
        result = 31 * result + expireAt.hashCode()
        return result
    }

    override fun toString(): String =
        "ForcedUpdate(channel=$channel, prev=$prev, target=$target, expireAt=$expireAt)"
}
