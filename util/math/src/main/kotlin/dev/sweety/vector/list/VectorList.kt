package dev.sweety.vector.list

import java.nio.ByteBuffer

/**
 * Base interface for all compact primitive vector array-lists.
 */
interface VectorList {
    fun serialize(): Map<String?, Any?> = emptyMap()
    fun size(): Int
    fun capacity(): Int
    fun isEmpty(): Boolean = size() == 0
    fun isNotEmpty(): Boolean = size() > 0
    fun clear()
    fun trim()
    fun byteSize(): Int
    fun writeTo(buffer: ByteBuffer)
}
