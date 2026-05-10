package dev.sweety.vector.common

import dev.sweety.vector.immutable.Vec3d
import dev.sweety.vector.mutable.MutVec3d
import kotlin.math.sqrt

interface Vector3d : Vector<Double, Vector3d> {
    fun x(): Double
    fun y(): Double
    fun z(): Double

    override fun add(other: Vector3d): Vector3d = add(other.x(), other.y(), other.z())
    fun add(x: Double, y: Double, z: Double): Vector3d = of(x() + x, y() + y, z() + z)

    override fun sub(other: Vector3d): Vector3d = sub(other.x(), other.y(), other.z())
    fun sub(x: Double, y: Double, z: Double): Vector3d = of(x() - x, y() - y, z() - z)

    override fun mul(other: Vector3d): Vector3d = mul(other.x(), other.y(), other.z())
    fun mul(x: Double, y: Double, z: Double): Vector3d = of(x() * x, y() * y, z() * z)

    override fun mul(value: Double): Vector3d = mul(value, value, value)

    override fun div(other: Vector3d): Vector3d = div(other.x(), other.y(), other.z())
    fun div(x: Double, y: Double, z: Double): Vector3d = of(x() / x, y() / y, z() / z)

    override fun div(value: Double): Vector3d = div(value, value, value)

    override fun dot(other: Vector3d): Double = x() * other.x() + y() * other.y() + z() * other.z()

    override fun length(): Double = sqrt(lengthSquared())
    override fun lengthSquared(): Double = x() * x() + y() * y() + z() * z()

    override fun distance(other: Vector3d): Double = sqrt(distanceSquared(other))

    fun distanceSquared(x: Double, y: Double, z: Double): Double {
        val dx = x() - x
        val dy = y() - y
        val dz = z() - z
        return dx * dx + dy * dy + dz * dz
    }
    override fun distanceSquared(other: Vector3d) = distanceSquared(other.x(), other.y(), other.z())

    override fun normalize(): Vector3d {
        val l = length()
        return if (l == 0.0) this else div(l)
    }

    fun with(x: Double? = null, y: Double? = null, z: Double? = null): Vector3d

    fun crossProduct(other: Vector3d): Vector3d {
        val newX = y() * other.z() - other.y() * z()
        val newY = z() * other.x() - other.z() * x()
        val newZ = x() * other.y() - other.x() * y()
        return of(newX, newY, newZ)
    }

    fun toMutable(): MutVec3d
    fun toImmutable(): Vec3d

    fun toVector3i(): Vector3i = Vector3i.of(x().toInt(), y().toInt(), z().toInt())
    fun toVector3f(): Vector3f = Vector3f.of(x().toFloat(), y().toFloat(), z().toFloat())

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "x" to x(),
            "y" to y(),
            "z" to z()
        )
    }

    fun string(precision: Int) = "x=${"%.${precision}f".format(x())}, y=${"%.${precision}f".format(y())}, z=${"%.${precision}f".format(z())}"

    companion object {
        fun of(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Vec3d = Vec3d(x, y, z)
        fun mutableOf(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): MutVec3d = MutVec3d(x, y, z)
    }
}
