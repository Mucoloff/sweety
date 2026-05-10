package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec3i
import dev.sweety.vector.mutable.MutVec3i
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface Vector3i : Vector<Int, Vector3i> {
    fun x(): Int
    fun y(): Int
    fun z(): Int

    override fun add(other: Vector3i): Vector3i = add(other.x(), other.y(), other.z())
    fun add(x: Int, y: Int, z: Int): Vector3i = Vector3i.of(x() + x, y() + y, z() + z)

    override fun sub(other: Vector3i): Vector3i = sub(other.x(), other.y(), other.z())
    fun sub(x: Int, y: Int, z: Int): Vector3i = Vector3i.of(x() - x, y() - y, z() - z)

    override fun mul(other: Vector3i): Vector3i = mul(other.x(), other.y(), other.z())
    fun mul(x: Int, y: Int, z: Int): Vector3i = Vector3i.of(x() * x, y() * y, z() * z)

    override fun mul(value: Int): Vector3i = mul(value, value, value)

    override fun div(other: Vector3i): Vector3i = div(other.x(), other.y(), other.z())
    fun div(x: Int, y: Int, z: Int): Vector3i = Vector3i.of(x() / x, y() / y, z() / z)

    override fun div(value: Int): Vector3i = div(value, value, value)

    override fun dot(other: Vector3i): Int = x() * other.x() + y() * other.y() + z() * other.z()

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = (x() * x() + y() * y() + z() * z()).toDouble()

    override fun distance(other: Vector3i): Double = sqrt(distanceSquared(other))
    override fun distanceSquared(other: Vector3i): Double {
        val dx = (x() - other.x()).toDouble()
        val dy = (y() - other.y()).toDouble()
        val dz = (z() - other.z()).toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    override fun normalize(): Vector3i {
        val l = length()
        if (l == 0.0) return this
        return Vector3i.of((x() / l).roundToInt(), (y() / l).roundToInt(), (z() / l).roundToInt())
    }

    fun with(x: Int? = null, y: Int? = null, z: Int? = null): Vector3i

    fun crossProduct(other: Vector3i): Vector3i {
        val newX = y() * other.z() - other.y() * z()
        val newY = z() * other.x() - other.z() * x()
        val newZ = x() * other.y() - other.x() * y()
        return Vector3i.of(newX, newY, newZ)
    }

    fun toMutable(): MutVec3i
    fun toImmutable(): Vec3i

    fun toVector3f(): Vector3f = Vector3f.of(x().toFloat(), y().toFloat(), z().toFloat())
    fun toVector3d(): Vector3d = Vector3d.of(x().toDouble(), y().toDouble(), z().toDouble())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y(),
            "z" to z()
        )
    }

    companion object {
        fun of(x: Int = 0, y: Int = 0, z: Int = 0): Vec3i = Vec3i(x, y, z)
        fun mutableOf(x: Int = 0, y: Int = 0, z: Int = 0): MutVec3i = MutVec3i(x, y, z)
    }
}
