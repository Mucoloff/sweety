package dev.sweety.vector.list

import dev.sweety.config.common.serialization.ConfigSerializable
import java.nio.ByteBuffer

/**
 * Base interface for packed primitive vector lists (zero-allocation arrays).
 */
interface VectorList : ConfigSerializable {
    fun size(): Int
    fun capacity(): Int
    fun isEmpty(): Boolean = size() == 0
    fun isNotEmpty(): Boolean = size() > 0
    fun clear()
    fun trim()
    fun byteSize(): Int
    fun writeTo(buffer: ByteBuffer)
}
