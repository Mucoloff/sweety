package dev.sweety.math.pool


import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Minimal object pool to recycle expensive instances and cut allocation / GC pressure.
 *
 * Three backing strategies, each with five lean implementations so a pool never carries a field or
 * branch it doesn't use:
 *
 *  - **normal** ([SimplePool], [ResetPool], [SizedPool], [SizedResetPool], [SizedDiscardPool]) —
 *    `ArrayDeque`, single-thread / release-on-any-thread-but-unsynchronized.
 *  - **threadLocal** ([LocalPool] … [SizedLocalDiscardPool]) — per-thread `ArrayDeque`, zero contention.
 *  - **shared** ([SharedPool] … [SizedSharedDiscardPool]) — lock-free `ConcurrentLinkedDeque`.
 *
 * Within each strategy: supplier-only, supplier+reset (both unbounded), then the sized trio
 * (supplier, supplier+reset, supplier+reset+discard). Pick via the [Companion] factories; they route
 * `null` reset/discard and `max < 0` to the leanest matching class.
 *
 * @param T pooled type
 */
interface ObjectPool<T> {
    @Acquire
    fun acquire(): T

    @Release
    fun release(obj: T)

    fun release(vararg objs: T) = objs.forEach { release(it) }

    /** Borrow for the duration of `fn`, then auto-release. */
    @Borrows
    fun <V> use(fn: Function<T, V>): V {
        val obj = acquire()
        try {
            return fn.apply(obj)
        } finally {
            release(obj)
        }
    }

    @Borrows
    fun consume(fn: Consumer<T>) {
        val obj = acquire()
        try {
            fn.accept(obj)
        } finally {
            release(obj)
        }
    }

    fun releaseIf(iterable: MutableIterable<T>, filter: Predicate<T>): Boolean {
        if (iterable is MutableCollection<T>) {
            val toRelease = ArrayList<T>()
            iterable.removeIf { item ->
                if (filter.test(item)) {
                    toRelease.add(item)
                    true
                } else {
                    false
                }
            }
            toRelease.forEach { release(it) }
            return toRelease.isNotEmpty()
        }

        var removed = false
        val each = iterable.iterator()
        var t: T
        while (each.hasNext()) {
            t = each.next()
            if (filter.test(t)) {
                each.remove()
                release(t)
                removed = true
            }
        }
        return removed
    }

    /** No pooling at all: always allocate, never retain. */
    class FakeObjectPool<T> internal constructor(private val factory: Supplier<T>) : ObjectPool<T> {
        override fun acquire(): T = factory.get()
        override fun release(obj: T) {}

        fun build(): ObjectPool<T> = this
    }

    // ========================== NORMAL (ArrayDeque) ==========================

    class SimplePool<T> internal constructor(
        private val factory: Supplier<T>, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val pool = newDeque<T>(initialCapacity)
        override fun acquire(): T = pool.removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            pool.addFirst(obj)
        }
    }

    class ResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val pool = newDeque<T>(initialCapacity)
        override fun acquire(): T = pool.removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            pool.addFirst(obj)
        }
    }

    class SizedPool<T> internal constructor(
        private val factory: Supplier<T>, private val max: Int, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val pool = newDeque<T>(initialCapacity)
        override fun acquire(): T = pool.removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            if (pool.size < max) pool.addFirst(obj)
        }
    }

    class SizedResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>, private val max: Int,
        initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val pool = newDeque<T>(initialCapacity)
        override fun acquire(): T = pool.removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            if (pool.size < max) pool.addFirst(obj)
        }
    }

    class SizedDiscardPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>,
        private val onDiscard: Consumer<T>, private val max: Int, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val pool = newDeque<T>(initialCapacity)
        override fun acquire(): T = pool.removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            if (pool.size < max) pool.addFirst(obj) else onDiscard.accept(obj)
        }
    }

    // ========================== THREAD-LOCAL ==========================

    class LocalPool<T> internal constructor(
        private val factory: Supplier<T>, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val tl = ThreadLocal.withInitial { newDeque<T>(initialCapacity) }
        override fun acquire(): T = tl.get().removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            tl.get().addFirst(obj)
        }
    }

    class LocalResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val tl = ThreadLocal.withInitial { newDeque<T>(initialCapacity) }
        override fun acquire(): T = tl.get().removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            tl.get().addFirst(obj)
        }
    }

    class SizedLocalPool<T> internal constructor(
        private val factory: Supplier<T>, private val max: Int, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val tl = ThreadLocal.withInitial { newDeque<T>(initialCapacity) }
        override fun acquire(): T = tl.get().removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            val d = tl.get()
            if (d.size < max) d.addFirst(obj)
        }
    }

    class SizedLocalResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>, private val max: Int,
        initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val tl = ThreadLocal.withInitial { newDeque<T>(initialCapacity) }
        override fun acquire(): T = tl.get().removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            val d = tl.get()
            if (d.size < max) d.addFirst(obj)
        }
    }

    class SizedLocalDiscardPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>,
        private val onDiscard: Consumer<T>, private val max: Int, initialCapacity: Int = -1
    ) : ObjectPool<T> {
        private val tl = ThreadLocal.withInitial { newDeque<T>(initialCapacity) }
        override fun acquire(): T = tl.get().removeFirstOrNull() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            val d = tl.get()
            if (d.size < max) d.addFirst(obj) else onDiscard.accept(obj)
        }
    }

    // ========================== SHARED (ConcurrentLinkedDeque) ==========================

    class SharedPool<T> internal constructor(private val factory: Supplier<T>) : ObjectPool<T> {
        private val deque = ConcurrentLinkedDeque<T & Any>()
        override fun acquire(): T = deque.pollFirst() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            deque.offerFirst(obj)
        }
    }

    class SharedResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>
    ) : ObjectPool<T> {
        private val deque = ConcurrentLinkedDeque<T & Any>()
        override fun acquire(): T = deque.pollFirst() ?: factory.get()
        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            deque.offerFirst(obj)
        }
    }

    class SizedSharedPool<T> internal constructor(
        private val factory: Supplier<T>, private val max: Int
    ) : ObjectPool<T> {
        private val deque = ConcurrentLinkedDeque<T & Any>()
        private val size = AtomicInteger()
        override fun acquire(): T {
            val obj = deque.pollFirst() ?: return factory.get()
            size.decrementAndGet()
            return obj
        }

        override fun release(obj: T) {
            if (obj == null) return
            var s: Int
            do {
                s = size.get()
                if (s >= max) return
            } while (!size.compareAndSet(s, s + 1))
            deque.offerFirst(obj)
        }
    }

    class SizedSharedResetPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>, private val max: Int
    ) : ObjectPool<T> {
        private val deque = ConcurrentLinkedDeque<T & Any>()
        private val size = AtomicInteger()
        override fun acquire(): T {
            val obj = deque.pollFirst() ?: return factory.get()
            size.decrementAndGet()
            return obj
        }

        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            var s: Int
            do {
                s = size.get()
                if (s >= max) return
            } while (!size.compareAndSet(s, s + 1))
            deque.offerFirst(obj)
        }
    }

    class SizedSharedDiscardPool<T> internal constructor(
        private val factory: Supplier<T>, private val reset: Consumer<T>,
        private val onDiscard: Consumer<T>, private val max: Int
    ) : ObjectPool<T> {
        private val deque = ConcurrentLinkedDeque<T & Any>()
        private val size = AtomicInteger()
        override fun acquire(): T {
            val obj = deque.pollFirst() ?: return factory.get()
            size.decrementAndGet()
            return obj
        }

        override fun release(obj: T) {
            if (obj == null) return
            reset.accept(obj)
            var s: Int
            do {
                s = size.get()
                if (s >= max) {
                    onDiscard.accept(obj)
                    return
                }
            } while (!size.compareAndSet(s, s + 1))
            deque.offerFirst(obj)
        }
    }

    enum class Strategy {
        SIMPLE,
        THREAD_LOCAL,
        SHARED,
        FAKE
    }

    class Builder<T>(
        private val factory: Supplier<T>,
        private val strategy: Strategy
    ) {
        private var reset: Consumer<T>? = null
        private var onDiscard: Consumer<T>? = null
        private var maxSize: Int = -1

        private var initialCapacity: Int = -1

        fun reset(fn: Consumer<T>) = apply { this.reset = fn }
        fun onDiscard(fn: Consumer<T>) = apply { this.onDiscard = fn }
        fun maxSize(max: Int) = apply { this.maxSize = max }
        fun initialCapacity(initialCapacity: Int) = apply { this.initialCapacity = initialCapacity }

        fun build(): ObjectPool<T> {
            return when (strategy) {
                Strategy.SIMPLE -> buildSimple()
                Strategy.THREAD_LOCAL -> buildLocal()
                Strategy.SHARED -> buildShared()
                Strategy.FAKE -> buildFake()
            }
        }

        private fun buildSimple(): ObjectPool<T> {
            val resetFn = reset
            val onDiscardFn = onDiscard
            return when {
                maxSize < 0 -> if (resetFn == null) {
                    SimplePool(factory, initialCapacity)
                } else {
                    ResetPool(factory, resetFn, initialCapacity)
                }

                onDiscardFn != null -> SizedDiscardPool(
                    factory,
                    resetFn ?: noop(),
                    onDiscardFn,
                    maxSize,
                    initialCapacity
                )

                resetFn != null -> SizedResetPool(factory, resetFn, maxSize, initialCapacity)
                else -> SizedPool(factory, maxSize, initialCapacity)
            }
        }

        private fun buildLocal(): ObjectPool<T> {
            val resetFn = reset
            val onDiscardFn = onDiscard
            return when {
                maxSize < 0 -> if (resetFn == null) {
                    LocalPool(factory, initialCapacity)
                } else {
                    LocalResetPool(factory, resetFn, initialCapacity)
                }

                onDiscardFn != null -> SizedLocalDiscardPool(
                    factory,
                    resetFn ?: noop(),
                    onDiscardFn,
                    maxSize,
                    initialCapacity
                )

                resetFn != null -> SizedLocalResetPool(factory, resetFn, maxSize, initialCapacity)
                else -> SizedLocalPool(factory, maxSize, initialCapacity)
            }
        }

        private fun buildShared(): ObjectPool<T> {
            val resetFn = reset
            val onDiscardFn = onDiscard
            return when {
                maxSize < 0 -> if (resetFn == null) {
                    SharedPool(factory)
                } else {
                    SharedResetPool(factory, resetFn)
                }

                onDiscardFn != null -> SizedSharedDiscardPool(factory, resetFn ?: noop(), onDiscardFn, maxSize)
                resetFn != null -> SizedSharedResetPool(factory, resetFn, maxSize)
                else -> SizedSharedPool(factory, maxSize)
            }
        }

        private fun buildFake(): ObjectPool<T> = FakeObjectPool(factory)
    }

    companion object {

        private val NOOP: Consumer<Any?> = Consumer { }

        @Suppress("UNCHECKED_CAST")
        private fun <T> noop(): Consumer<T> = NOOP as Consumer<T>


        @JvmStatic
        fun <T> simple(factory: Supplier<T>) = Builder(factory, Strategy.SIMPLE)

        @JvmStatic
        fun <T> threadLocal(factory: Supplier<T>) = Builder(factory, Strategy.THREAD_LOCAL)

        @JvmStatic
        fun <T> shared(factory: Supplier<T>) = Builder(factory, Strategy.SHARED)

        @JvmStatic
        fun <T> fake(factory: Supplier<T>) = FakeObjectPool(factory)
    }
}

private fun <T> newDeque(initialCapacity: Int): ArrayDeque<T> =
    if (initialCapacity > 0) ArrayDeque<T>(initialCapacity) else ArrayDeque<T>()
