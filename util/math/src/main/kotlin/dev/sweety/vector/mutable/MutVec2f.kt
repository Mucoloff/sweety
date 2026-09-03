package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector2f
import dev.sweety.vector.immutable.Vec2f

/**
 * Mutable 2D float Vector.
 */
class MutVec2f(
    private var x: Float = 0f,
    private var y: Float = 0f
) : Vector2f {

    override fun x() = x
    override fun y() = y

    constructor(immutable: Vector2f) : this(immutable.x(), immutable.y())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toFloat() ?: 0.0f,
        (me["y"] as? Number)?.toFloat() ?: 0.0f
    )

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun set(other: Vector2f) = set(other.x(), other.y())

    fun selfAdd(other: Vector2f) = selfAdd(other.x(), other.y())
    fun selfAdd(x: Float, y: Float) {
        this.x += x
        this.y += y
    }

    fun selfSub(other: Vector2f) = selfSub(other.x(), other.y())
    fun selfSub(x: Float, y: Float) {
        this.x -= x
        this.y -= y
    }

    fun selfMul(other: Vector2f) = selfMul(other.x(), other.y())
    fun selfMul(x: Float, y: Float) {
        this.x *= x
        this.y *= y
    }

    fun selfMul(value: Float) = selfMul(value, value)

    fun selfDiv(other: Vector2f) = selfDiv(other.x(), other.y())
    fun selfDiv(x: Float, y: Float) {
        this.x /= x
        this.y /= y
    }

    fun selfDiv(value: Float) = selfDiv(value, value)

    fun selfNormalize() {
        val l = length().toFloat()
        if (l != 0f) {
            selfDiv(l)
        }
    }

    override fun with(x: Float?, y: Float?): Vector2f = Vec2f(x ?: this.x, y ?: this.y)

    override fun toImmutable() = Vec2f(x, y)
    override fun toMutable() = this

    override fun toString(): String = "MutVec2f(x=$x, y=$y)"
}
