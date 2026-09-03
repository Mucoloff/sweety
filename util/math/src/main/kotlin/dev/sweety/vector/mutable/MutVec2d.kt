package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector2d
import dev.sweety.vector.immutable.Vec2d

/**
 * Mutable 2D double Vector.
 */
class MutVec2d(
    private var x: Double = 0.0,
    private var y: Double = 0.0
) : Vector2d {

    override fun x() = x
    override fun y() = y

    constructor(immutable: Vector2d) : this(immutable.x(), immutable.y())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toDouble() ?: 0.0,
        (me["y"] as? Number)?.toDouble() ?: 0.0
    )

    fun set(x: Double, y: Double) {
        this.x = x
        this.y = y
    }

    fun set(other: Vector2d) = set(other.x(), other.y())

    fun selfAdd(other: Vector2d) = selfAdd(other.x(), other.y())
    fun selfAdd(x: Double, y: Double) {
        this.x += x
        this.y += y
    }

    fun selfSub(other: Vector2d) = selfSub(other.x(), other.y())
    fun selfSub(x: Double, y: Double) {
        this.x -= x
        this.y -= y
    }

    fun selfMul(other: Vector2d) = selfMul(other.x(), other.y())
    fun selfMul(x: Double, y: Double) {
        this.x *= x
        this.y *= y
    }

    fun selfMul(value: Double) = selfMul(value, value)

    fun selfDiv(other: Vector2d) = selfDiv(other.x(), other.y())
    fun selfDiv(x: Double, y: Double) {
        this.x /= x
        this.y /= y
    }

    fun selfDiv(value: Double) = selfDiv(value, value)

    fun selfNormalize() {
        val l = length()
        if (l != 0.0) {
            selfDiv(l)
        }
    }

    override fun with(x: Double?, y: Double?): Vector2d = Vec2d(x ?: this.x, y ?: this.y)

    override fun toImmutable() = Vec2d(x, y)
    override fun toMutable() = this

    override fun toString(): String = "MutVec2d(x=$x, y=$y)"

}
