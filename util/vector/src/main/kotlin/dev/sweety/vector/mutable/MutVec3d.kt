package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector3d
import dev.sweety.vector.immutable.Vec3d

/**
 * Mutable 3D double Vector.
 */
class MutVec3d(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0
) : Vector3d {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(immutable: Vector3d) : this(immutable.x(), immutable.y(), immutable.z())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toDouble() ?: 0.0,
        (me["y"] as? Number)?.toDouble() ?: 0.0,
        (me["z"] as? Number)?.toDouble() ?: 0.0
    )

    fun set(x: Double, y: Double, z: Double) = apply {
        this.x = x
        this.y = y
        this.z = z
    }

    fun set(other: Vector3d) = set(other.x(), other.y(), other.z())

    fun selfAdd(other: Vector3d) = selfAdd(other.x(), other.y(), other.z())
    fun selfAdd(x: Double, y: Double, z: Double) {
        this.x += x
        this.y += y
        this.z += z
    }

    fun selfSub(other: Vector3d) = selfSub(other.x(), other.y(), other.z())
    fun selfSub(x: Double, y: Double, z: Double) = apply {
        this.x -= x
        this.y -= y
        this.z -= z
    }

    fun selfMul(other: Vector3d) = selfMul(other.x(), other.y(), other.z())
    fun selfMul(x: Double, y: Double, z: Double) {
        this.x *= x
        this.y *= y
        this.z *= z
    }
    fun selfMul(value: Double) = selfMul(value, value, value)

    fun selfDiv(other: Vector3d) = selfDiv(other.x(), other.y(), other.z())
    fun selfDiv(x: Double, y: Double, z: Double) {
        this.x /= x
        this.y /= y
        this.z /= z
    }
    fun selfDiv(value: Double) = selfDiv(value, value, value)

    fun selfNormalize() {
        val l = length()
        if (l != 0.0) {
            selfDiv(l)
        }
    }

    fun selfCrossProduct(other: Vector3d) {
        val newX = this.y * other.z() - other.y() * this.z
        val newY = this.z * other.x() - other.z() * this.x
        val newZ = this.x * other.y() - other.x() * this.y
        set(newX, newY, newZ)
    }

    override fun with(x: Double?, y: Double?, z: Double?) = Vec3d(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toImmutable() = Vec3d(x, y, z)
    override fun toMutable() = this

    override fun toString(): String = "MutVec3d(x=$x, y=$y, z=$z)"

}
