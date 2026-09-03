package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector3i
import dev.sweety.vector.immutable.Vec3i
import kotlin.math.roundToInt

/**
 * Mutable 3D int Vector.
 */
class MutVec3i(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0
) : Vector3i {

    override fun x() = x
    override fun y() = y
    override fun z() = z

    constructor(immutable: Vector3i) : this(immutable.x(), immutable.y(), immutable.z())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toInt() ?: 0,
        (me["y"] as? Number)?.toInt() ?: 0,
        (me["z"] as? Number)?.toInt() ?: 0
    )

    fun set(x: Int, y: Int, z: Int) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun set(other: Vector3i) = set(other.x(), other.y(), other.z())

    fun selfAdd(other: Vector3i) = selfAdd(other.x(), other.y(), other.z())
    fun selfAdd(x: Int, y: Int, z: Int) {
        this.x += x
        this.y += y
        this.z += z
    }

    fun selfSub(other: Vector3i) = selfSub(other.x(), other.y(), other.z())
    fun selfSub(x: Int, y: Int, z: Int) {
        this.x -= x
        this.y -= y
        this.z -= z
    }

    fun selfMul(other: Vector3i) = selfMul(other.x(), other.y(), other.z())
    fun selfMul(x: Int, y: Int, z: Int) {
        this.x *= x
        this.y *= y
        this.z *= z
    }
    fun selfMul(value: Int) = selfMul(value, value, value)

    fun selfDiv(other: Vector3i) = selfDiv(other.x(), other.y(), other.z())
    fun selfDiv(x: Int, y: Int, z: Int) {
        this.x /= x
        this.y /= y
        this.z /= z
    }
    fun selfDiv(value: Int) = selfDiv(value, value, value)

    fun selfNormalize() {
        val l = length()
        if (l != 0.0) {
            x = (x / l).roundToInt()
            y = (y / l).roundToInt()
            z = (z / l).roundToInt()
        }
    }

    fun selfCrossProduct(other: Vector3i) {
        val newX = this.y * other.z() - other.y() * this.z
        val newY = this.z * other.x() - other.z() * this.x
        val newZ = this.x * other.y() - other.x() * this.y
        set(newX, newY, newZ)
    }

    override fun with(x: Int?, y: Int?, z: Int?): Vector3i = Vec3i(x ?: this.x, y ?: this.y, z ?: this.z)

    override fun toImmutable() = Vec3i(x, y, z)
    override fun toMutable() = this

    override fun toString(): String = "MutVec3i(x=$x, y=$y, z=$z)"
}
