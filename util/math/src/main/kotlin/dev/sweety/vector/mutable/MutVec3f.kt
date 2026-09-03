package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector3f
import dev.sweety.vector.immutable.Vec3f

/**
 * Mutable 3D float Vector.
 */
class MutVec3f(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) : Vector3f {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(immutable: Vector3f) : this(immutable.x(), immutable.y(), immutable.z())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toFloat() ?: 0.0f,
        (me["y"] as? Number)?.toFloat() ?: 0.0f,
        (me["z"] as? Number)?.toFloat() ?: 0.0f
    )

    fun set(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun set(other: Vector3f) = set(other.x(), other.y(), other.z())


    fun selfAdd(other: Vector3f) = selfAdd(other.x(), other.y(), other.z())
    fun selfAdd(x: Float, y: Float, z: Float) {
        this.x += x
        this.y += y
        this.z += z
    }

    fun selfSub(other: Vector3f) = selfSub(other.x(), other.y(), other.z())
    fun selfSub(x: Float, y: Float, z: Float) {
        this.x -= x
        this.y -= y
        this.z -= z
    }

    fun selfMul(other: Vector3f) = selfMul(other.x(), other.y(), other.z())
    fun selfMul(x: Float, y: Float, z: Float) {
        this.x *= x
        this.y *= y
        this.z *= z
    }
    fun selfMul(value: Float) = selfMul(value, value, value)

    fun selfDiv(other: Vector3f) = selfDiv(other.x(), other.y(), other.z())
    fun selfDiv(x: Float, y: Float, z: Float) {
        this.x /= x
        this.y /= y
        this.z /= z
    }
    fun selfDiv(value: Float) = selfDiv(value, value, value)

    fun selfNormalize() {
        val l = length().toFloat()
        if (l != 0f) {
            selfDiv(l)
        }
    }

    fun selfCrossProduct(other: Vector3f) {
        val newX = this.y * other.z() - other.y() * this.z
        val newY = this.z * other.x() - other.z() * this.x
        val newZ = this.x * other.y() - other.x() * this.y
        set(newX, newY, newZ)
    }

    override fun with(x: Float?, y: Float?, z: Float?) = Vec3f(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toImmutable() = Vec3f(x, y, z)
    override fun toMutable() = this

    override fun toString(): String = "MutVec3f(x=$x, y=$y, z=$z)"
}
