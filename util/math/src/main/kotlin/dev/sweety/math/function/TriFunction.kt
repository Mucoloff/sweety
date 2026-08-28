package dev.sweety.math.function

fun interface TriFunction<R, T, U, V> {
    fun apply(t: T, u: U, v: V): R
}
