package dev.sweety.versioning.server.data

import dev.sweety.time.Expirable
import dev.sweety.versioning.protocol.handshake.DownloadType
import dev.sweety.versioning.server.Settings
import dev.sweety.versioning.version.Version
import dev.sweety.versioning.version.artifact.Artifact
import dev.sweety.versioning.version.channel.Channel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Base extensible download token implementing Sweety [Expirable].
 * Open OOP class designed to be extended by platform modules (such as Luce SaaS).
 */
open class Token(
    open val clientId: UUID,
    open val artifact: Artifact,
    open val channel: Channel,
    open val version: Version,
    open val from: Version?,
    open val downloadType: DownloadType,
    open val expireAt: Long,
    open val token: UUID = generateTokenUuid(clientId, artifact, version, channel, downloadType, expireAt)
) : Expirable {

    @JvmOverloads
    constructor(
        clientId: UUID,
        artifact: Artifact,
        channel: Channel,
        version: Version,
        from: Version?,
        downloadType: DownloadType,
        delayMs: Long = Settings.DOWNLOAD_EXPIRE_DELAY_MS
    ) : this(
        clientId = clientId,
        artifact = artifact,
        channel = channel,
        version = version,
        from = from,
        downloadType = downloadType,
        expireAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs)
    )

    // Expirable contract & Java record compatibility accessors
    fun clientId(): UUID = clientId
    fun artifact(): Artifact = artifact
    fun channel(): Channel = channel
    fun version(): Version = version
    fun from(): Version? = from
    fun downloadType(): DownloadType = downloadType
    override fun expireAt(): Long = expireAt
    fun token(): UUID = token

    override fun toString(): String {
        return "Token(clientId=$clientId, artifact=$artifact, channel=$channel, version=$version, from=$from, downloadType=$downloadType, expireAt=$expireAt, token=$token)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false
        return token == other.token
    }

    override fun hashCode(): Int = token.hashCode()

    companion object {
        private val SECRET: ByteArray = Settings.TOKEN_GEN_SALT.toByteArray(StandardCharsets.UTF_8)
        private const val HMAC_ALGO = "HmacSHA256"

        /**
         * Generic, cryptographically secure RFC-4122 compliant UUID derivation via HMAC-SHA256.
         */
        @JvmStatic
        @JvmOverloads
        fun deriveUuid(secret: ByteArray = SECRET, feedBytes: (Mac) -> Unit): UUID {
            val mac = Mac.getInstance(HMAC_ALGO)
            mac.init(SecretKeySpec(secret, HMAC_ALGO))
            feedBytes(mac)
            val hmac = mac.doFinal()
            val hashBuf = ByteBuffer.wrap(hmac)

            // Format as RFC-4122 v4 UUID (type 4, variant 1)
            val msb = (hashBuf.getLong() and 0x000000000000F000L.inv()) or 0x0000000000004000L
            val lsb = (hashBuf.getLong() and 0x3fffffffffffffffL) or Long.MIN_VALUE
            return UUID(msb, lsb)
        }

        /**
         * Fast, cryptographically secure RFC-4122 compliant UUID derivation via HMAC-SHA256.
         * Eliminates redundant CRC32 allocations while guaranteeing non-collision and tamper resistance.
         */
        @JvmStatic
        fun generateTokenUuid(
            clientId: UUID,
            artifact: Artifact,
            version: Version,
            channel: Channel,
            downloadType: DownloadType,
            expireAt: Long
        ): UUID = deriveUuid { mac ->
            val buffer = ByteBuffer.allocate(16 + 4 + 12 + 4 + 4 + 8).apply {
                putLong(clientId.mostSignificantBits)
                putLong(clientId.leastSignificantBits)
                putInt(artifactKey(artifact))
                putInt(version.major())
                putInt(version.minor())
                putInt(version.patch())
                putInt(channel.ordinal)
                putInt(downloadType.ordinal)
                putLong(expireAt)
            }
            mac.update(buffer.array())
        }

        @JvmStatic
        private fun artifactKey(artifact: Artifact): Int = when (artifact.name()) {
            "APP" -> 0
            "LAUNCHER" -> 1
            else -> artifact.name().hashCode()
        }
    }
}
