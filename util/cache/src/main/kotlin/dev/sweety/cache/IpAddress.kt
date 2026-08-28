package dev.sweety.cache

import dev.sweety.data.buffer.BufferReader
import dev.sweety.data.buffer.BufferWriter
import dev.sweety.data.buffer.io.AbstractCodec
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * A client IP as its raw address bytes (4 for IPv4, 16 for IPv6) plus the display string — the
 * value type shared by [IpRateLimiter] and abuse-throttling code so keys hash/compare on the real
 * address bytes instead of a UTF-8-encoded string.
 *
 * [AbstractCodec]: writes only the raw bytes on the wire — [address] is derived from them on read,
 * never itself serialized, so a packet field never pays for a redundant string encoding of an IP.
 */
class IpAddress private constructor(
    var bytes: ByteArray,
    private var address: String
) : AbstractCodec {

    /** No-arg ctor for [AbstractCodec] discovery/registries that construct-then-{@link #read}. */
    constructor() : this(ByteArray(0), "")

    fun isBlank(): Boolean = address.isBlank()

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean =
        this === other || (other is IpAddress && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun write(buffer: BufferWriter) {
        buffer.writeByteArray(*bytes)
    }

    override fun read(buffer: BufferReader) {
        bytes = buffer.readByteArray()
        address = try {
            InetAddress.getByAddress(bytes).hostAddress
        } catch (e: UnknownHostException) {
            // getByAddress only validates array length (4/16 bytes) — a malformed peer payload
            // still decodes to a usable key, just without a pretty display string.
            bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
        }
    }

    companion object {
        @JvmStatic
        fun of(bytes: ByteArray, address: String): IpAddress = IpAddress(bytes, address)
    }
}
