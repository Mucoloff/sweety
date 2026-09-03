package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec2f
import dev.sweety.vector.mutable.MutVec2f
import kotlin.math.sqrt

interface Vector2f : Vector<Float, Vector2f> {
    fun x(): Float
    fun y(): Float

    override fun add(other: Vector2f): Vector2f = add(other.x(), other.y())
    fun add(x: Float, y: Float): Vector2f = Vector2f.of(x() + x, y() + y)

    override fun sub(other: Vector2f): Vector2f = sub(other.x(), other.y())
    fun sub(x: Float, y: Float): Vector2f = Vector2f.of(x() - x, y() - y)

    override fun mul(other: Vector2f): Vector2f = mul(other.x(), other.y())
    fun mul(x: Float, y: Float): Vector2f = Vector2f.of(x() * x, y() * y)

    override fun mul(value: Float): Vector2f = mul(value, value)

    override fun div(other: Vector2f): Vector2f = div(other.x(), other.y())
    fun div(x: Float, y: Float): Vector2f = Vector2f.of(x() / x, y() / y)

    override fun div(value: Float): Vector2f = div(value, value)

    override fun dot(other: Vector2f): Float = x() * other.x() + y() * other.y()

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = (x() * x() + y() * y()).toDouble()

    fun distanceSquared(x: Float, y: Float): Double {
        val dx = (x() - x).toDouble()
        val dy = y() - y
        return dx * dx + dy * dy
    }

    fun distance(x: Float, y: Float) = sqrt(distanceSquared(x, y))
    override fun distanceSquared(other: Vector2f) = distanceSquared(other.x(), other.y())

    override fun distance(other: Vector2f) = sqrt(distanceSquared(other))

    override fun normalize(): Vector2f {
        val l = length().toFloat()
        return if (l == 0f) this else div(l)
    }

    fun with(x: Float? = null, y: Float? = null): Vector2f

    fun toMutable(): MutVec2f
    fun toImmutable(): Vec2f

    fun toVector2i(): Vector2i = Vector2i.of(x().toInt(), y().toInt())
    fun toVector2d(): Vector2d = Vector2d.of(x().toDouble(), y().toDouble())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y()
        )
    }

    fun string(precision: Int) = "x=${"%.${precision}f".format(x())}, y=${"%.${precision}f".format(y())}"

    companion object {
        fun of(x: Float = 0f, y: Float = 0f): Vec2f = Vec2f(x, y)
        fun mutableOf(x: Float = 0f, y: Float = 0f): MutVec2f = MutVec2f(x, y)
    }
}
