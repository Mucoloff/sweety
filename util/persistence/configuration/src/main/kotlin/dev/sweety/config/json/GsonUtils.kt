package dev.sweety.config.json

import com.google.gson.Gson
import java.io.Reader
import java.lang.reflect.Type

object GsonUtils {

    private val gson: ThreadLocal<Gson> =
        ThreadLocal.withInitial { Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create() }

    @JvmStatic
    fun gson(): Gson = gson.get()

    @JvmStatic
    fun <T> write(obj: T): String = gson().toJson(obj)

    @JvmStatic
    fun <T> write(obj: T, type: Type): String = gson().toJson(obj, type)

    @JvmStatic
    fun <T> save(config: T, writer: Appendable) {
        gson().toJson(config, writer)
    }

    @JvmStatic
    fun <T> save(config: T, type: Type, writer: Appendable) {
        gson().toJson(config, type, writer)
    }

    @JvmStatic
    fun <T> load(reader: Reader, configClass: Class<T>): T = gson().fromJson(reader, configClass)

    @JvmStatic
    fun <T> read(obj: String, clazz: Class<T>): T = gson().fromJson(obj, clazz)

    @JvmStatic
    fun <T> load(reader: Reader, configClass: Type): T = gson().fromJson(reader, configClass)

    @JvmStatic
    fun <T> read(obj: String, clazz: Type): T = gson().fromJson(obj, clazz)
}
