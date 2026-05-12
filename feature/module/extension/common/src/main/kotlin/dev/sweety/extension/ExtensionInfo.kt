package dev.sweety.extension

import dev.sweety.config.json.GsonUtils
import dev.sweety.extension.exception.ExtensionNotFoundException
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.jar.JarFile

data class ExtensionInfo(
    private val name: String,
    private val version: String,
    private val main: String,
    private val description: String? = null
) {

    companion object {
        private val BASE: ExtensionInfo = ExtensionInfo("name", "version", "mainClass", "optional description")

        @JvmStatic
        @Throws(Exception::class)
        fun of(path: Path, extensionName: String): ExtensionInfo {
            JarFile(path.toFile()).use { jar ->
                val entry = jar.getJarEntry("$extensionName.json") ?: throw ExtensionNotFoundException(
                    extensionName, path.toString(), GsonUtils.write(
                        BASE, ExtensionInfo::class.java
                    )
                )
                InputStreamReader(jar.getInputStream(entry)).use { reader ->
                    return GsonUtils.load(reader, ExtensionInfo::class.java)
                }
            }
        }
    }

    fun name(): String = name
    fun version(): String = version
    fun main(): String = main
    fun description(): String? = description

    override fun toString(): String {
        return "$name-$version (main: $main, description: ${description ?: "none"})"
    }
}
