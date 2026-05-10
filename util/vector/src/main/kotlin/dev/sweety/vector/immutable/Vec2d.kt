package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector2d
import dev.sweety.vector.mutable.MutVec2d

/**
 * Immutable 2D double Vector.
 */
data class Vec2d(
    val x: Double = 0.0,
    val y: Double = 0.0
) : Vector2d {

    override fun x() = x
    override fun y() = y

    constructor(array: DoubleArray) : this(
        if (array.isNotEmpty()) array[0] else 0.0,
        if (array.size > 1) array[1] else 0.0
    )


    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toDouble() ?: 0.0,
        (me["y"] as? Number)?.toDouble() ?: 0.0
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector2d) return false
        return x == other.x() && y == other.y()
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    override fun add(x: Double, y: Double) = Vec2d(this.x + x, this.y + y)

    override fun sub(x: Double, y: Double) = Vec2d(this.x - x, this.y - y)

    override fun mul(x: Double, y: Double) = Vec2d(this.x * x, this.y * y)

    override fun div(x: Double, y: Double) = Vec2d(this.x / x, this.y / y)

    override fun with(x: Double?, y: Double?): Vector2d = Vec2d(x ?: this.x, y ?: this.y)

    override fun toMutable() = MutVec2d(x, y)
    override fun toImmutable() = this

    override fun toString() = "Vec2d(x=$x, y=$y)"


    companion object {
        @JvmStatic
        fun zero() = Vec2d()
    }
}
