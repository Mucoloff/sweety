package dev.sweety.vector.immutable

import dev.sweety.vector.common.Vector2i
import dev.sweety.vector.mutable.MutVec2i
import kotlin.math.roundToInt

/**
 * Immutable 2D int Vector.
 */
data class Vec2i(
    val x: Int = 0,
    val y: Int = 0
) : Vector2i {

    override fun x() = x
    override fun y() = y

    constructor(array: IntArray) : this(
        if (array.isNotEmpty()) array[0] else 0,
        if (array.size > 1) array[1] else 0
    )


    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toInt() ?: 0,
        (me["y"] as? Number)?.toInt() ?: 0
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector2i) return false
        return x == other.x() && y == other.y()
    }

    override fun hashCode(): Int {
        var result = x
        result = 31 * result + y
        return result
    }

    override fun add(x: Int, y: Int) = Vec2i(this.x + x, this.y + y)

    override fun sub(x: Int, y: Int) = Vec2i(this.x - x, this.y - y)

    override fun mul(x: Int, y: Int) = Vec2i(this.x * x, this.y * y)

    override fun div(x: Int, y: Int) = Vec2i(this.x / x, this.y / y)

    override fun normalize(): Vec2i {
        val l = length()
        if (l == 0.0) return this
        return Vec2i((x / l).roundToInt(), (y / l).roundToInt())
    }

    override fun with(x: Int?, y: Int?): Vector2i = Vec2i(x ?: this.x, y ?: this.y)

    override fun toMutable() = MutVec2i(x, y)
    override fun toImmutable() = this

    override fun toString() = "Vec2i(x=$x, y=$y)"


    companion object {
        @JvmStatic
        fun zero() = Vec2i()
    }
}
