package dev.sweety.math.pool

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Universal registry for [ObjectPool] instances of arbitrary internal and external classes.
 */
object Pools {

    private val registry = ConcurrentHashMap<Class<*>, ObjectPool<*>>()

    init {
        // Register commonly pooled standard JDK types by default
        register(StringBuilder::class.java, { StringBuilder(128) }, { it.setLength(0) })
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> register(type: Class<T>, pool: ObjectPool<T>): ObjectPool<T> {
        registry[type] = pool
        return pool
    }

    @JvmStatic
    fun <T : Any> register(type: Class<T>, factory: Supplier<T>, reset: Consumer<T>? = null): ObjectPool<T> {
        val builder = ObjectPool.threadLocal(factory)
        if (reset != null) {
            builder.reset(reset)
        }
        val pool = builder.build()
        return register(type, pool)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>): ObjectPool<T> {
        return (registry[type] as? ObjectPool<T>)
            ?: throw IllegalArgumentException("No ObjectPool registered for class: ${type.name}")
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreate(type: Class<T>, defaultFactory: Supplier<T>): ObjectPool<T> {
        return (registry.computeIfAbsent(type) {
            ObjectPool.threadLocal(defaultFactory).build()
        } as ObjectPool<T>)
    }

    @JvmStatic
    fun <T : Any> acquire(type: Class<T>): T = get(type).acquire()

    @JvmStatic
    fun <T : Any> release(type: Class<T>, obj: T) = get(type).release(obj)

    @JvmStatic
    fun <T : Any> borrow(type: Class<T>): PoolHandle<T> = get(type).borrow()
}
