package dev.sweety.vector.mutable

import dev.sweety.vector.common.Vector2i
import dev.sweety.vector.immutable.Vec2i
import kotlin.math.roundToInt

/**
 * Mutable 2D int Vector.
 */
class MutVec2i(
    private var x: Int = 0,
    private var y: Int = 0
) : Vector2i {

    override fun x() = x
    override fun y() = y

    constructor(immutable: Vector2i) : this(immutable.x(), immutable.y())

    constructor(me: Map<String, Any?>) : this(
        (me["x"] as? Number)?.toInt() ?: 0,
        (me["y"] as? Number)?.toInt() ?: 0
    )

    fun set(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun set(other: Vector2i) = set(other.x(), other.y())

    fun selfAdd(other: Vector2i) = selfAdd(other.x(), other.y())
    fun selfAdd(x: Int, y: Int) {
        this.x += x
        this.y += y
    }

    fun selfSub(other: Vector2i) = selfSub(other.x(), other.y())
    fun selfSub(x: Int, y: Int) {
        this.x -= x
        this.y -= y
    }

    fun selfMul(other: Vector2i) = selfMul(other.x(), other.y())
    fun selfMul(x: Int, y: Int) {
        this.x *= x
        this.y *= y
    }

    fun selfMul(value: Int) = selfMul(value, value)

    fun selfDiv(other: Vector2i) = selfDiv(other.x(), other.y())
    fun selfDiv(x: Int, y: Int) {
        this.x /= x
        this.y /= y
    }

    fun selfDiv(value: Int) = selfDiv(value, value)

    fun selfNormalize() {
        val l = length()
        if (l != 0.0) {
            x = (x / l).roundToInt()
            y = (y / l).roundToInt()
        }
    }

    override fun with(x: Int?, y: Int?): Vector2i = Vec2i(x ?: this.x, y ?: this.y)

    override fun toImmutable() = Vec2i(x, y)
    override fun toMutable() = this

    override fun toString(): String = "MutVec2i(x=$x, y=$y)"

}
