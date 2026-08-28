package dev.sweety.math.pool

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.IntFunction
import java.util.function.ToIntFunction
import kotlin.math.max

/**
 * Pooling interface for reusable arrays of any type.
 * 
 * 
 * Two implementations are available via the factory methods:
 * 
 *  * [.threadLocal] — per-thread ArrayDeque, zero contention.
 *  * [.shared] — ConcurrentLinkedDeque, safe for cross-thread use.
 * Fixes the peek/pollFirst TOCTOU present in the previous implementation:
 * `pollFirst()` is called directly; if the taken array is too small it is
 * dropped (not returned to the pool) and a fresh one is allocated.
 * 
 * 
 * 
 * Both variants accept any array type via `IntFunction<T>` (factory) and
 * `ToIntFunction<T>` (length extractor). Convenience factories for `byte[]`,
 * `int[]`, and `float[]` are provided.
 */
interface ArrayPool<T> {
    /**
     * Returns an array whose length is `>= minSize`.
     * The returned array may be larger than requested.
     */
    @Acquire
    fun acquire(minSize: Int): T

    /**
     * Returns `arr` to the pool if it is within the acceptable size range.
     */
    @Release
    fun release(arr: T)

    /**
     * Borrows an array of at least `minSize`, applies `fn`, releases it, yields result.
     */
    @Borrows
    fun <V> use(minSize: Int, fn: Function<T, V>): V {
        val arr = acquire(minSize)
        try {
            return fn.apply(arr)
        } finally {
            release(arr)
        }
    }

    @Borrows
    fun consume(minSize: Int, fn: Consumer<T>) {
        val arr = acquire(minSize)
        try {
            fn.accept(arr)
        } finally {
            release(arr)
        }
    }

    // ========================== IMPLEMENTATIONS ==========================
    class ThreadLocalPool<T> internal constructor(
        private val factory: IntFunction<T>, private val length: ToIntFunction<T>,
        private val defaultSize: Int, private val onDiscard: Consumer<T>?, private val maxPerThread: Int
    ) : ArrayPool<T> {
        private val pool: ThreadLocal<ArrayDeque<T>> = ThreadLocal.withInitial { ArrayDeque() }

        override fun acquire(minSize: Int): T {
            val deque = pool.get()
            // Fast path: head is large enough
            val head = deque.firstOrNull()
            if (head != null && length.applyAsInt(head) >= minSize) return deque.removeFirst()
            // Slow path: scan for first array that fits, leave the rest in place
            val it = deque.iterator()
            while (it.hasNext()) {
                val arr = it.next()
                if (length.applyAsInt(arr) >= minSize) {
                    it.remove()
                    return arr
                }
            }
            return factory.apply(max(defaultSize, minSize))
        }

        override fun release(arr: T) {
            if (arr == null) return
            val len = length.applyAsInt(arr)
            if (len < defaultSize / 2 || len > defaultSize * 2) {
                onDiscard?.accept(arr)
                return
            }
            val deque = pool.get()
            if (deque.size < maxPerThread) deque.addFirst(arr)
            else onDiscard?.accept(arr)
        }
    }

    class SharedPool<T> internal constructor(
        private val factory: IntFunction<T>, private val length: ToIntFunction<T>,
        private val defaultSize: Int, private val onDiscard: Consumer<T>?, private val maxPoolSize: Int
    ) : ArrayPool<T> {
        private val pool = ConcurrentLinkedDeque<T & Any>()
        private val count = AtomicInteger()

        override fun acquire(minSize: Int): T {
            // pollFirst directly — no peek/poll TOCTOU
            val arr = pool.pollFirst()
            if (arr != null) {
                count.decrementAndGet()
                if (length.applyAsInt(arr) >= minSize) return arr
                // too small — drop it, allocate fresh
            }
            return factory.apply(max(defaultSize, minSize))
        }

        override fun release(arr: T) {
            if (arr == null) return
            val len = length.applyAsInt(arr)
            if (len < defaultSize * 0.5 || len > defaultSize * 2) {
                onDiscard?.accept(arr)
                return
            }
            var c: Int
            do {
                c = count.get()
                if (c >= maxPoolSize) {
                    onDiscard?.accept(arr)
                    return
                }
            } while (!count.compareAndSet(c, c + 1))
            pool.offerFirst(arr)
        }
    }

    enum class Strategy {
        THREAD_LOCAL,
        SHARED
    }

    class Builder<T>(
        private val factory: IntFunction<T>,
        private val length: ToIntFunction<T>
    ) {
        private var defaultSize: Int = 0
        private var onDiscard: Consumer<T>? = null
        private var maxSize: Int = -1
        private var strategy: Strategy = Strategy.THREAD_LOCAL

        fun defaultSize(defaultSize: Int) = apply { this.defaultSize = defaultSize }
        fun onDiscard(fn: Consumer<T>) = apply { this.onDiscard = fn }
        fun maxSize(maxSize: Int) = apply { this.maxSize = maxSize }
        fun strategy(strategy: Strategy) = apply { this.strategy = strategy }

        fun build(): ArrayPool<T> {
            return when (strategy) {
                Strategy.THREAD_LOCAL -> buildThreadLocal()
                Strategy.SHARED -> buildShared()
            }
        }

        private fun buildThreadLocal(): ArrayPool<T> = when {
            maxSize < 0 -> ThreadLocalPool(factory, length, defaultSize, onDiscard, Int.MAX_VALUE)
            onDiscard != null -> ThreadLocalPool(factory, length, defaultSize, onDiscard, maxSize)
            else -> ThreadLocalPool(factory, length, defaultSize, null, maxSize)
        }

        private fun buildShared(): ArrayPool<T> = when {
            maxSize < 0 -> SharedPool(factory, length, defaultSize, onDiscard, Int.MAX_VALUE)
            onDiscard != null -> SharedPool(factory, length, defaultSize, onDiscard, maxSize)
            else -> SharedPool(factory, length, defaultSize, null, maxSize)
        }
    }

    companion object {
        @JvmStatic
        fun <T> builder(factory: IntFunction<T>, length: ToIntFunction<T>): Builder<T> =
            Builder(factory, length)

        @JvmStatic
        fun <T> threadLocal(
            factory: IntFunction<T>, length: ToIntFunction<T>,
            defaultSize: Int,
            maxPerThread: Int,
            onDiscard: Consumer<T>? = null
        ): ArrayPool<T> = if (onDiscard == null) {
            Builder(factory, length)
                .defaultSize(defaultSize)
                .maxSize(maxPerThread)
                .build()
        } else {
            Builder(factory, length)
                .defaultSize(defaultSize)
                .onDiscard(onDiscard)
                .maxSize(maxPerThread)
                .build()
        }

        @JvmStatic
        fun <T> shared(
            factory: IntFunction<T>, length: ToIntFunction<T>,
            defaultSize: Int,
            maxPoolSize: Int,
            onDiscard: Consumer<T>? = null
        ): ArrayPool<T> = if (onDiscard == null) {
            Builder(factory, length)
                .strategy(Strategy.SHARED)
                .defaultSize(defaultSize)
                .maxSize(maxPoolSize)
                .build()
        } else {
            Builder(factory, length)
                .strategy(Strategy.SHARED)
                .defaultSize(defaultSize)
                .onDiscard(onDiscard)
                .maxSize(maxPoolSize)
                .build()
        }
    }
}
