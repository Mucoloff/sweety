package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector3d
import dev.sweety.vector.mutable.MutVec3d

/**
 * Immutable 3D double Vector.
 */
data class Vec3d(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0
) : Vector3d {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(array: DoubleArray) : this(
        if (array.isNotEmpty()) array[0] else 0.0,
        if (array.size > 1) array[1] else 0.0,
        if (array.size > 2) array[2] else 0.0
    )

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toDouble() ?: 0.0,
        (me["y"] as? Number)?.toDouble() ?: 0.0,
        (me["z"] as? Number)?.toDouble() ?: 0.0
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector3d) return false
        return x == other.x() && y == other.y() && z == other.z()
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun add(x: Double, y: Double, z: Double) = Vec3d(this.x + x, this.y + y, this.z + z)

    override fun sub(x: Double, y: Double, z: Double) = Vec3d(this.x - x, this.y - y, this.z - z)

    override fun mul(x: Double, y: Double, z: Double) = Vec3d(this.x * x, this.y * y, this.z * z)

    override fun div(x: Double, y: Double, z: Double) = Vec3d(this.x / x, this.y / y, this.z / z)

    override fun with(x: Double?, y: Double?, z: Double?) = Vec3d(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toMutable() = MutVec3d(x, y, z)
    override fun toImmutable() = this

    override fun toString() = "Vec3d(x=$x, y=$y, z=$z)"


    companion object {
        @JvmStatic
        fun zero() = Vec3d()
    }
}
