package dev.sweety.versioning.server.data

import dev.sweety.versioning.version.channel.Channel
import java.time.Instant
import java.util.UUID

/**
 * Extensible client identity containing unique ID and subscription channel.
 */
open class ClientInfo(
    open val id: UUID,
    open val channel: Channel
) {
    fun id(): UUID = id
    fun channel(): Channel = channel

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClientInfo) return false
        return id == other.id && channel == other.channel
    }

    override fun hashCode(): Int = 31 * id.hashCode() + channel.hashCode()

    override fun toString(): String = "ClientInfo(id=$id, channel=$channel)"
}

/**
 * Extensible client tracking profile with session timestamps.
 */
open class ClientProfile(
    open val clientId: UUID,
    open val channel: Channel,
    open val firstSeen: Instant = Instant.now(),
    open val lastSeen: Instant = Instant.now()
) {
    fun clientId(): UUID = clientId
    fun channel(): Channel = channel
    fun firstSeen(): Instant = firstSeen
    fun lastSeen(): Instant = lastSeen

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClientProfile) return false
        return clientId == other.clientId
    }

    override fun hashCode(): Int = clientId.hashCode()

    override fun toString(): String =
        "ClientProfile(clientId=$clientId, channel=$channel, firstSeen=$firstSeen, lastSeen=$lastSeen)"
}
