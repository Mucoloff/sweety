package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec2d
import dev.sweety.vector.mutable.MutVec2d
import kotlin.math.sqrt

interface Vector2d : Vector<Double, Vector2d> {
    fun x(): Double
    fun y(): Double

    override fun add(other: Vector2d): Vector2d = add(other.x(), other.y())
    fun add(x: Double, y: Double): Vector2d = Vector2d.of(x() + x, y() + y)

    override fun sub(other: Vector2d): Vector2d = sub(other.x(), other.y())
    fun sub(x: Double, y: Double): Vector2d = Vector2d.of(x() - x, y() - y)

    override fun mul(other: Vector2d): Vector2d = mul(other.x(), other.y())
    fun mul(x: Double, y: Double): Vector2d = Vector2d.of(x() * x, y() * y)

    override fun mul(value: Double): Vector2d = mul(value, value)

    override fun div(other: Vector2d): Vector2d = div(other.x(), other.y())
    fun div(x: Double, y: Double): Vector2d = Vector2d.of(x() / x, y() / y)

    override fun div(value: Double): Vector2d = div(value, value)

    override fun dot(other: Vector2d): Double = x() * other.x() + y() * other.y()

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = x() * x() + y() * y()

    fun distanceSquared(x: Double, y: Double): Double {
        val dx = (x() - x)
        val dy = y() - y
        return dx * dx + dy * dy
    }

    fun distance(x: Double, y: Double) = sqrt(distanceSquared(x, y))
    override fun distanceSquared(other: Vector2d) = distanceSquared(other.x(), other.y())

    override fun distance(other: Vector2d) = sqrt(distanceSquared(other))

    override fun normalize(): Vector2d {
        val l = length()
        return if (l == 0.0) this else div(l)
    }

    fun with(x: Double? = null, y: Double? = null): Vector2d

    fun toMutable(): MutVec2d
    fun toImmutable(): Vec2d

    fun toVector2i(): Vector2i = Vector2i.of(x().toInt(), y().toInt())
    fun toVector2f(): Vector2f = Vector2f.of(x().toFloat(), y().toFloat())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y()
        )
    }

    fun string(precision: Int) = "x=${"%.${precision}f".format(x())}, y=${"%.${precision}f".format(y())}"

    companion object {
        fun of(x: Double = 0.0, y: Double = 0.0): Vec2d = Vec2d(x, y)
        fun mutableOf(x: Double = 0.0, y: Double = 0.0): MutVec2d = MutVec2d(x, y)
    }
}
