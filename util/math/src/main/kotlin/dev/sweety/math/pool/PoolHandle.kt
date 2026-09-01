package dev.sweety.math.pool

/**
 * An [AutoCloseable] handle wrapping a pooled object to enable try-with-resources in Java
 * and `.use { ... }` in Kotlin with zero manual release boilerplate.
 *
 * Example in Java:
 * ```java
 * try (PoolHandle<StringBuilder> handle = pool.borrow()) {
 *     StringBuilder sb = handle.get();
 *     sb.append("data");
 * }
 * ```
 *
 * Example in Kotlin:
 * ```kotlin
 * pool.borrow().use { handle ->
 *     val sb = handle.get()
 *     sb.append("data")
 * }
 * ```
 */
class PoolHandle<T>(
    private val pool: ObjectPool<T>,
    private var value: T?
) : AutoCloseable {

    fun get(): T {
        return value ?: throw IllegalStateException("PoolHandle has already been closed/released")
    }

    override fun close() {
        val current = value
        if (current != null) {
            value = null
            pool.release(current)
        }
    }
}
