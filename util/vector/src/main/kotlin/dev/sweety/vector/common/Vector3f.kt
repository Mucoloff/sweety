package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec3f
import dev.sweety.vector.mutable.MutVec3f
import kotlin.math.sqrt

interface Vector3f : Vector<Float, Vector3f> {
    fun x(): Float
    fun y(): Float
    fun z(): Float

    override fun add(other: Vector3f): Vector3f = add(other.x(), other.y(), other.z())
    fun add(x: Float, y: Float, z: Float): Vector3f = of(x() + x, y() + y, z() + z)

    override fun sub(other: Vector3f): Vector3f = sub(other.x(), other.y(), other.z())
    fun sub(x: Float, y: Float, z: Float): Vector3f = of(x() - x, y() - y, z() - z)

    override fun mul(other: Vector3f): Vector3f = mul(other.x(), other.y(), other.z())
    fun mul(x: Float, y: Float, z: Float): Vector3f = of(x() * x, y() * y, z() * z)

    override fun mul(value: Float): Vector3f = mul(value, value, value)

    override fun div(other: Vector3f): Vector3f = div(other.x(), other.y(), other.z())
    fun div(x: Float, y: Float, z: Float): Vector3f = of(x() / x, y() / y, z() / z)

    override fun div(value: Float): Vector3f = div(value, value, value)

    override fun dot(other: Vector3f): Float = x() * other.x() + y() * other.y() + z() * other.z()
    fun dot(x: Float, y: Float, z: Float): Float = x() * x + y() * y + z() * z

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = (x() * x() + y() * y() + z() * z()).toDouble()

    override fun distance(other: Vector3f): Double = sqrt(distanceSquared(other))

    override fun distanceSquared(other: Vector3f): Double {
        val dx = (x() - other.x()).toDouble()
        val dy = (y() - other.y()).toDouble()
        val dz = (z() - other.z()).toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    override fun normalize(): Vector3f {
        val l = length().toFloat()
        return if (l == 0f) this else div(l)
    }

    fun with(x: Float? = null, y: Float? = null, z: Float? = null): Vector3f

    fun crossProduct(other: Vector3f): Vector3f {
        val newX = y() * other.z() - other.y() * z()
        val newY = z() * other.x() - other.z() * x()
        val newZ = x() * other.y() - other.x() * y()
        return of(newX, newY, newZ)
    }

    fun toMutable(): MutVec3f
    fun toImmutable(): Vec3f

    fun toVector3i(): Vector3i = Vector3i.of(x().toInt(), y().toInt(), z().toInt())
    fun toVector3d(): Vector3d = Vector3d.of(x().toDouble(), y().toDouble(), z().toDouble())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y(),
            "z" to z()
        )
    }

    fun string(precision: Int) = "x=${"%.${precision}f".format(x())}, y=${"%.${precision}f".format(y())}, z=${"%.${precision}f".format(z())}"

    companion object {
        fun of(x: Float = 0f, y: Float = 0f, z: Float = 0f): Vec3f = Vec3f(x, y, z)
        fun mutableOf(x: Float = 0f, y: Float = 0f, z: Float = 0f): MutVec3f = MutVec3f(x, y, z)
    }
}
