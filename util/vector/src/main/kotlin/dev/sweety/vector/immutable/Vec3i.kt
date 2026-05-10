package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector3i
import dev.sweety.vector.mutable.MutVec3i
import kotlin.math.roundToInt

/**
 * Immutable 3D int Vector.
 */
data class Vec3i(
    val x: Int = 0,
    val y: Int = 0,
    val z: Int = 0
) : Vector3i {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(array: IntArray) : this(
        if (array.isNotEmpty()) array[0] else 0,
        if (array.size > 1) array[1] else 0,
        if (array.size > 2) array[2] else 0
    )

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toInt() ?: 0,
        (me["y"] as? Number)?.toInt() ?: 0,
        (me["z"] as? Number)?.toInt() ?: 0
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector3i) return false
        return x == other.x() && y == other.y() && z == other.z()
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        result = 31 * result + z
        return result
    }

    override fun add(x: Int, y: Int, z: Int) = Vec3i(this.x + x, this.y + y, this.z + z)

    override fun sub(x: Int, y: Int, z: Int) = Vec3i(this.x - x, this.y - y, this.z - z)

    override fun mul(x: Int, y: Int, z: Int) = Vec3i(this.x * x, this.y * y, this.z * z)

    override fun div(x: Int, y: Int, z: Int) = Vec3i(this.x / x, this.y / y, this.z / z)

    override fun normalize(): Vec3i {
        val l = length()
        if (l == 0.0) return this
        return Vec3i((x / l).roundToInt(), (y / l).roundToInt(), (z / l).roundToInt())
    }

    override fun with(x: Int?, y: Int?, z: Int?) = Vec3i(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toMutable() = MutVec3i(x, y, z)
    override fun toImmutable() = this

    override fun toString() = "Vec3i(x=$x, y=$y, z=$z)"


    companion object {
        @JvmStatic
        fun zero() = Vec3i()
    }
}
