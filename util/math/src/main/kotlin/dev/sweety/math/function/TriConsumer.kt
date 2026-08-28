package dev.sweety.math.function

fun interface TriConsumer<T, U, V> {
    fun accept(t: T, u: U, v: V)
}
