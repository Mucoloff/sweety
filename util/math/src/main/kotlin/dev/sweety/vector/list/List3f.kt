package dev.sweety.vector.list

import dev.sweety.vector.common.Vector3f
import dev.sweety.vector.mutable.MutVec3f
import java.nio.ByteBuffer

/**
 * High-performance packed primitive 3D float list (Structure of Arrays / Flat array).
 */
class List3f(initialCapacity: Int = 16) : VectorList {

    @PublishedApi
    internal var data: FloatArray = FloatArray(initialCapacity * 3)
    @PublishedApi
    internal var size: Int = 0

    override fun size(): Int = size
    override fun capacity(): Int = data.size / 3
    override fun clear() { size = 0 }
    override fun byteSize(): Int = size * 3 * java.lang.Float.BYTES

    private fun ensureCapacity(minVectorCapacity: Int) {
        val minElements = minVectorCapacity * 3
        if (minElements > data.size) {
            val newCapacity = Math.max(data.size * 2, minElements)
            val newArray = FloatArray(newCapacity)
            System.arraycopy(data, 0, newArray, 0, size * 3)
            data = newArray
        }
    }

    override fun trim() {
        if (size * 3 < data.size) {
            val newArray = FloatArray(size * 3)
            System.arraycopy(data, 0, newArray, 0, size * 3)
            data = newArray
        }
    }

    fun add(x: Float, y: Float, z: Float) {
        ensureCapacity(size + 1)
        val idx = size * 3
        data[idx] = x
        data[idx + 1] = y
        data[idx + 2] = z
        size++
    }

    fun add(vec: Vector3f) = add(vec.x(), vec.y(), vec.z())

    fun set(index: Int, x: Float, y: Float, z: Float) {
        checkIndex(index)
        val idx = index * 3
        data[idx] = x
        data[idx + 1] = y
        data[idx + 2] = z
    }

    fun set(index: Int, vec: Vector3f) = set(index, vec.x(), vec.y(), vec.z())

    fun setX(index: Int, x: Float) { checkIndex(index); data[index * 3] = x }
    fun setY(index: Int, y: Float) { checkIndex(index); data[index * 3 + 1] = y }
    fun setZ(index: Int, z: Float) { checkIndex(index); data[index * 3 + 2] = z }

    fun getX(index: Int): Float { checkIndex(index); return data[index * 3] }
    fun getY(index: Int): Float { checkIndex(index); return data[index * 3 + 1] }
    fun getZ(index: Int): Float { checkIndex(index); return data[index * 3 + 2] }

    fun get(index: Int, out: MutVec3f): MutVec3f {
        checkIndex(index)
        val idx = index * 3
        out.set(data[idx], data[idx + 1], data[idx + 2])
        return out
    }

    fun add(index: Int, dx: Float, dy: Float, dz: Float) {
        checkIndex(index)
        val idx = index * 3
        data[idx] += dx
        data[idx + 1] += dy
        data[idx + 2] += dz
    }

    fun sub(index: Int, dx: Float, dy: Float, dz: Float) {
        checkIndex(index)
        val idx = index * 3
        data[idx] -= dx
        data[idx + 1] -= dy
        data[idx + 2] -= dz
    }

    fun mul(index: Int, factor: Float) {
        checkIndex(index)
        val idx = index * 3
        data[idx] *= factor
        data[idx + 1] *= factor
        data[idx + 2] *= factor
    }

    fun div(index: Int, divisor: Float) {
        checkIndex(index)
        val idx = index * 3
        data[idx] /= divisor
        data[idx + 1] /= divisor
        data[idx + 2] /= divisor
    }

    inline fun forEach(action: (x: Float, y: Float, z: Float) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 3
            action(data[idx], data[idx + 1], data[idx + 2])
        }
    }

    inline fun forEachIndexed(action: (index: Int, x: Float, y: Float, z: Float) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 3
            action(i, data[idx], data[idx + 1], data[idx + 2])
        }
    }

    override fun writeTo(buffer: ByteBuffer) {
        for (i in 0 until size * 3) {
            buffer.putFloat(data[i])
        }
    }

    fun readFrom(buffer: ByteBuffer, count: Int) {
        ensureCapacity(size + count)
        for (i in 0 until count * 3) {
            data[size * 3 + i] = buffer.getFloat()
        }
        size += count
    }

    fun raw(): FloatArray = data

    override fun serialize(): Map<String?, Any?> {
        val list = ArrayList<Float>(size * 3)
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
        fun deserialize(map: Map<String, Any?>): List3f {
            val size = (map["size"] as? Number)?.toInt() ?: 0
            val list = List3f(size)
            val elements = map["data"] as? List<Number>
            if (elements != null) {
                for (i in 0 until (elements.size / 3)) {
                    val idx = i * 3
                    list.add(
                        elements[idx].toFloat(),
                        elements[idx + 1].toFloat(),
                        elements[idx + 2].toFloat()
                    )
                }
            }
            return list
        }
    }
}
