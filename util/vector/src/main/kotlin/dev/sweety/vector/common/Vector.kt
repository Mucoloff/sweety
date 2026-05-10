package dev.sweety.vector.common

import dev.sweety.config.common.serialization.ConfigSerializable

/**
 * Base interface for all vector types.
 * @param N The numeric type (Int, Float, Double)
 * @param Self The implementation type for fluent API
 */
interface Vector<N : Number, Self : Vector<N, Self>> : ConfigSerializable {
    fun add(other: Self): Self
    fun sub(other: Self): Self
    fun mul(other: Self): Self
    fun mul(value: N): Self
    fun div(other: Self): Self
    fun div(value: N): Self

    fun dot(other: Self): N

    fun length(): Double
    fun lengthSquared(): Double
    fun distance(other: Self): Double
    fun distanceSquared(other: Self): Double

    fun normalize(): Self
}
