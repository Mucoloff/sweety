package dev.sweety.vector.list

import dev.sweety.vector.common.Vector3i
import dev.sweety.vector.mutable.MutVec3i
import java.nio.ByteBuffer

/**
 * High-performance packed primitive 3D int list (Structure of Arrays / Flat array).
 */
class List3i(initialCapacity: Int = 16) : VectorList {

    @PublishedApi
    internal var data: IntArray = IntArray(initialCapacity * 3)
    @PublishedApi
    internal var size: Int = 0

    override fun size(): Int = size
    override fun capacity(): Int = data.size / 3
    override fun clear() { size = 0 }
    override fun byteSize(): Int = size * 3 * java.lang.Integer.BYTES

    private fun ensureCapacity(minVectorCapacity: Int) {
        val minElements = minVectorCapacity * 3
        if (minElements > data.size) {
            val newCapacity = Math.max(data.size * 2, minElements)
            val newArray = IntArray(newCapacity)
            System.arraycopy(data, 0, newArray, 0, size * 3)
            data = newArray
        }
    }

    override fun trim() {
        if (size * 3 < data.size) {
            val newArray = IntArray(size * 3)
            System.arraycopy(data, 0, newArray, 0, size * 3)
            data = newArray
        }
    }

    fun add(x: Int, y: Int, z: Int) {
        ensureCapacity(size + 1)
        val idx = size * 3
        data[idx] = x
        data[idx + 1] = y
        data[idx + 2] = z
        size++
    }

    fun add(vec: Vector3i) = add(vec.x(), vec.y(), vec.z())

    fun set(index: Int, x: Int, y: Int, z: Int) {
        checkIndex(index)
        val idx = index * 3
        data[idx] = x
        data[idx + 1] = y
        data[idx + 2] = z
    }

    fun set(index: Int, vec: Vector3i) = set(index, vec.x(), vec.y(), vec.z())

    fun setX(index: Int, x: Int) { checkIndex(index); data[index * 3] = x }
    fun setY(index: Int, y: Int) { checkIndex(index); data[index * 3 + 1] = y }
    fun setZ(index: Int, z: Int) { checkIndex(index); data[index * 3 + 2] = z }

    fun getX(index: Int): Int { checkIndex(index); return data[index * 3] }
    fun getY(index: Int): Int { checkIndex(index); return data[index * 3 + 1] }
    fun getZ(index: Int): Int { checkIndex(index); return data[index * 3 + 2] }

    fun get(index: Int, out: MutVec3i): MutVec3i {
        checkIndex(index)
        val idx = index * 3
        out.set(data[idx], data[idx + 1], data[idx + 2])
        return out
    }

    fun add(index: Int, dx: Int, dy: Int, dz: Int) {
        checkIndex(index)
        val idx = index * 3
        data[idx] += dx
        data[idx + 1] += dy
        data[idx + 2] += dz
    }

    fun sub(index: Int, dx: Int, dy: Int, dz: Int) {
        checkIndex(index)
        val idx = index * 3
        data[idx] -= dx
        data[idx + 1] -= dy
        data[idx + 2] -= dz
    }

    fun mul(index: Int, factor: Int) {
        checkIndex(index)
        val idx = index * 3
        data[idx] *= factor
        data[idx + 1] *= factor
        data[idx + 2] *= factor
    }

    fun div(index: Int, divisor: Int) {
        checkIndex(index)
        val idx = index * 3
        data[idx] /= divisor
        data[idx + 1] /= divisor
        data[idx + 2] /= divisor
    }

    inline fun forEach(action: (x: Int, y: Int, z: Int) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 3
            action(data[idx], data[idx + 1], data[idx + 2])
        }
    }

    inline fun forEachIndexed(action: (index: Int, x: Int, y: Int, z: Int) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 3
            action(i, data[idx], data[idx + 1], data[idx + 2])
        }
    }

    override fun writeTo(buffer: ByteBuffer) {
        for (i in 0 until size * 3) {
            buffer.putInt(data[i])
        }
    }

    fun readFrom(buffer: ByteBuffer, count: Int) {
        ensureCapacity(size + count)
        for (i in 0 until count * 3) {
            data[size * 3 + i] = buffer.getInt()
        }
        size += count
    }

    fun raw(): IntArray = data

    override fun serialize(): Map<String?, Any?> {
        val list = ArrayList<Int>(size * 3)
        for (i in 0 until size * 3) {
            list.add(data[i])
        }
        return mapOf("size" to size, "data" to list)
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun deserialize(map: Map<String, Any?>): List3i {
            val size = (map["size"] as? Number)?.toInt() ?: 0
            val list = List3i(size)
            val elements = map["data"] as? List<Number>
            if (elements != null) {
                for (i in 0 until (elements.size / 3)) {
                    val idx = i * 3
                    list.add(
                        elements[idx].toInt(),
                        elements[idx + 1].toInt(),
                        elements[idx + 2].toInt()
                    )
                }
            }
            return list
        }
    }
}
