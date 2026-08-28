package dev.sweety.versioning.util

import com.google.gson.Gson
import dev.sweety.data.ObjectUtils
import dev.sweety.versioning.version.Version
import java.util.UUID

object Utils {

    private val GSON: ThreadLocal<Gson> =
        ThreadLocal.withInitial { Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create() }

    @JvmStatic
    fun gson(): Gson = GSON.get()

    @JvmStatic
    fun toBytes(uuid: UUID): ByteArray = ObjectUtils.uuidToBytes(uuid)

    @JvmStatic
    fun toBytes(version: Version): ByteArray = toBytes(version.major(), version.minor(), version.patch())

    @JvmStatic
    fun toBytes(vararg value: Int): ByteArray {
        val bytes = ByteArray(value.size * 4)
        for (i in value.indices) {
            val v = value[i]
            val off = i * 4
            bytes[off] = (v ushr 24).toByte()
            bytes[off + 1] = (v ushr 16).toByte()
            bytes[off + 2] = (v ushr 8).toByte()
            bytes[off + 3] = v.toByte()
        }
        return bytes
    }
}
