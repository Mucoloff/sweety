package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector3f
import dev.sweety.vector.mutable.MutVec3f

/**
 * Immutable 3D float Vector.
 */
data class Vec3f(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) : Vector3f {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(array: FloatArray) : this(
        if (array.isNotEmpty()) array[0] else 0f,
        if (array.size > 1) array[1] else 0f,
        if (array.size > 2) array[2] else 0f
    )

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toFloat() ?: 0.0f,
        (me["y"] as? Number)?.toFloat() ?: 0.0f,
        (me["z"] as? Number)?.toFloat() ?: 0.0f
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector3f) return false
        return x == other.x() && y == other.y() && z == other.z()
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun add(x: Float, y: Float, z: Float) = Vec3f(this.x + x, this.y + y, this.z + z)

    override fun sub(x: Float, y: Float, z: Float) = Vec3f(this.x - x, this.y - y, this.z - z)

    override fun mul(x: Float, y: Float, z: Float) = Vec3f(this.x * x, this.y * y, this.z * z)

    override fun div(x: Float, y: Float, z: Float) = Vec3f(this.x / x, this.y / y, this.z / z)

    override fun with(x: Float?, y: Float?, z: Float?) = Vec3f(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toMutable() = MutVec3f(x, y, z)
    override fun toImmutable() = this

    override fun toString() = "Vec3f(x=$x, y=$y, z=$z)"


    companion object {
        @JvmStatic
        fun zero() = Vec3f()
    }
}
