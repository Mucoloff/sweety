package dev.sweety.exception

import java.io.PrintWriter
import java.io.StringWriter
import java.util.function.Consumer

object ExceptionUtils {

    @JvmStatic
    fun <T> throwIfAnyEquals(message: String, ifEquals: T, vararg toCheck: T) {
        for (o in toCheck) {
            if (o === ifEquals) throw IllegalArgumentException(message)
        }
    }

    @JvmStatic
    fun <T> throwSilently(func: ThrowingSupplier<T>, errorHandler: Consumer<Throwable>): T? {
        return try {
            func.get()
        } catch (t: Throwable) {
            errorHandler.accept(t)
            null
        }
    }

    @JvmStatic
    fun <T> throwSilently(func: ThrowingSupplier<T>): T? = throwSilently(func) { }

    fun interface ThrowingSupplier<T> {
        @Throws(Throwable::class)
        fun get(): T
    }

    @JvmStatic
    fun getStackTrace(throwable: Throwable?): String {
        if (throwable == null) return ""
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw, true))
        return sw.toString()
    }
}
