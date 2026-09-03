package dev.sweety.vector.list

import dev.sweety.vector.common.Vector2d
import dev.sweety.vector.mutable.MutVec2d
import java.nio.ByteBuffer

/**
 * High-performance packed primitive 2D double list (Structure of Arrays / Flat array).
 */
class List2d(initialCapacity: Int = 16) : VectorList {

    @PublishedApi
    internal var data: DoubleArray = DoubleArray(initialCapacity * 2)
    @PublishedApi
    internal var size: Int = 0

    override fun size(): Int = size
    override fun capacity(): Int = data.size / 2
    override fun clear() { size = 0 }
    override fun byteSize(): Int = size * 2 * java.lang.Double.BYTES

    private fun ensureCapacity(minVectorCapacity: Int) {
        val minElements = minVectorCapacity * 2
        if (minElements > data.size) {
            val newCapacity = Math.max(data.size * 2, minElements)
            val newArray = DoubleArray(newCapacity)
            System.arraycopy(data, 0, newArray, 0, size * 2)
            data = newArray
        }
    }

    override fun trim() {
        if (size * 2 < data.size) {
            val newArray = DoubleArray(size * 2)
            System.arraycopy(data, 0, newArray, 0, size * 2)
            data = newArray
        }
    }

    fun add(x: Double, y: Double) {
        ensureCapacity(size + 1)
        val idx = size * 2
        data[idx] = x
        data[idx + 1] = y
        size++
    }

    fun add(vec: Vector2d) = add(vec.x(), vec.y())

    fun set(index: Int, x: Double, y: Double) {
        checkIndex(index)
        val idx = index * 2
        data[idx] = x
        data[idx + 1] = y
    }

    fun set(index: Int, vec: Vector2d) = set(index, vec.x(), vec.y())

    fun setX(index: Int, x: Double) { checkIndex(index); data[index * 2] = x }
    fun setY(index: Int, y: Double) { checkIndex(index); data[index * 2 + 1] = y }

    fun getX(index: Int): Double { checkIndex(index); return data[index * 2] }
    fun getY(index: Int): Double { checkIndex(index); return data[index * 2 + 1] }

    fun get(index: Int, out: MutVec2d): MutVec2d {
        checkIndex(index)
        val idx = index * 2
        out.set(data[idx], data[idx + 1])
        return out
    }

    fun add(index: Int, dx: Double, dy: Double) {
        checkIndex(index)
        val idx = index * 2
        data[idx] += dx
        data[idx + 1] += dy
    }

    fun sub(index: Int, dx: Double, dy: Double) {
        checkIndex(index)
        val idx = index * 2
        data[idx] -= dx
        data[idx + 1] -= dy
    }

    fun mul(index: Int, factor: Double) {
        checkIndex(index)
        val idx = index * 2
        data[idx] *= factor
        data[idx + 1] *= factor
    }

    fun div(index: Int, divisor: Double) {
        checkIndex(index)
        val idx = index * 2
        data[idx] /= divisor
        data[idx + 1] /= divisor
    }

    inline fun forEach(action: (x: Double, y: Double) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 2
            action(data[idx], data[idx + 1])
        }
    }

    inline fun forEachIndexed(action: (index: Int, x: Double, y: Double) -> Unit) {
        for (i in 0 until size) {
            val idx = i * 2
            action(i, data[idx], data[idx + 1])
        }
    }

    override fun writeTo(buffer: ByteBuffer) {
        for (i in 0 until size * 2) {
            buffer.putDouble(data[i])
        }
    }

    fun readFrom(buffer: ByteBuffer, count: Int) {
        ensureCapacity(size + count)
        for (i in 0 until count * 2) {
            data[size * 2 + i] = buffer.getDouble()
        }
        size += count
    }

    fun raw(): DoubleArray = data

    override fun serialize(): Map<String?, Any?> {
        val list = ArrayList<Double>(size * 2)
        for (i in 0 until size * 2) {
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
        fun deserialize(map: Map<String, Any?>): List2d {
            val size = (map["size"] as? Number)?.toInt() ?: 0
            val list = List2d(size)
            val elements = map["data"] as? List<Number>
            if (elements != null) {
                for (i in 0 until (elements.size / 2)) {
                    val idx = i * 2
                    list.add(
                        elements[idx].toDouble(),
                        elements[idx + 1].toDouble()
                    )
                }
            }
            return list
        }
    }
}
