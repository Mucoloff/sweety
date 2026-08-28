package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec2i
import dev.sweety.vector.mutable.MutVec2i
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface Vector2i : Vector<Int, Vector2i> {
    fun x(): Int
    fun y(): Int

    override fun add(other: Vector2i): Vector2i = add(other.x(), other.y())
    fun add(x: Int, y: Int): Vector2i = Vector2i.of(x() + x, y() + y)

    override fun sub(other: Vector2i): Vector2i = sub(other.x(), other.y())
    fun sub(x: Int, y: Int): Vector2i = Vector2i.of(x() - x, y() - y)

    override fun mul(other: Vector2i): Vector2i = mul(other.x(), other.y())
    fun mul(x: Int, y: Int): Vector2i = Vector2i.of(x() * x, y() * y)

    override fun mul(value: Int): Vector2i = mul(value, value)

    override fun div(other: Vector2i): Vector2i = div(other.x(), other.y())
    fun div(x: Int, y: Int): Vector2i = Vector2i.of(x() / x, y() / y)

    override fun div(value: Int): Vector2i = div(value, value)

    override fun dot(other: Vector2i): Int = x() * other.x() + y() * other.y()

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = (x() * x() + y() * y()).toDouble()

    fun distanceSquared(x: Int, y: Int): Double {
        val dx = (x() - x).toDouble()
        val dy = y() - y
        return dx * dx + dy * dy
    }

    fun distance(x: Int, y: Int) = sqrt(distanceSquared(x, y))
    override fun distanceSquared(other: Vector2i) = distanceSquared(other.x(), other.y())

    override fun distance(other: Vector2i) = sqrt(distanceSquared(other))

    override fun normalize(): Vector2i {
        val l = length()
        if (l == 0.0) return this
        return Vector2i.of((x() / l).roundToInt(), (y() / l).roundToInt())
    }

    fun with(x: Int? = null, y: Int? = null): Vector2i

    fun toMutable(): MutVec2i
    fun toImmutable(): Vec2i

    fun toVector2f(): Vector2f = Vector2f.of(x().toFloat(), y().toFloat())
    fun toVector2d(): Vector2d = Vector2d.of(x().toDouble(), y().toDouble())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y()
        )
    }

    companion object {
        fun of(x: Int = 0, y: Int = 0): Vec2i = Vec2i(x, y)
        fun mutableOf(x: Int = 0, y: Int = 0): MutVec2i = MutVec2i(x, y)
    }
}
