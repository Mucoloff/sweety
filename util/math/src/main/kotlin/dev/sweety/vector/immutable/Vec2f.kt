package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector2f
import dev.sweety.vector.mutable.MutVec2f

/**
 * Immutable 2D float Vector.
 */
data class Vec2f(
    val x: Float = 0f,
    val y: Float = 0f
) : Vector2f {

    override fun x() = x
    override fun y() = y

    constructor(array: FloatArray) : this(
        if (array.isNotEmpty()) array[0] else 0f,
        if (array.size > 1) array[1] else 0f
    )

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toFloat() ?: 0.0f,
        (me["y"] as? Number)?.toFloat() ?: 0.0f
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector2f) return false
        return x == other.x() && y == other.y()
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    override fun add(x: Float, y: Float) = Vec2f(this.x + x, this.y + y)

    override fun sub(x: Float, y: Float) = Vec2f(this.x - x, this.y - y)

    override fun mul(x: Float, y: Float) = Vec2f(this.x * x, this.y * y)

    override fun div(x: Float, y: Float) = Vec2f(this.x / x, this.y / y)

    override fun with(x: Float?, y: Float?): Vector2f = Vec2f(x ?: this.x, y ?: this.y)

    override fun toMutable() = MutVec2f(x, y)
    override fun toImmutable() = this

    override fun toString() = "Vec2f(x=$x, y=$y)"


    companion object {
        @JvmStatic
        fun zero() = Vec2f()
    }
}
