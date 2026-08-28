package dev.sweety.extension

import dev.sweety.config.common.ConfigurationSection
import dev.sweety.config.common.serialization.ConfigSerializable
import dev.sweety.config.yml.YamlUtils
import dev.sweety.extension.exception.ExtensionNotFoundException
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.jar.JarFile

data class ExtensionInfo(
    private val name: String,
    private val version: String,
    private val main: String,
    private val description: String? = null,
    /**
     * Fully-qualified classes that must be loadable for this extension to work at all — a hard
     * dependency on something outside the build, typically another mod. An extension that wraps a
     * third-party mod declares it here so the manager can decline to load it rather than let it
     * fail later, mid-tick, as a NoClassDefFoundError.
     */
    private val requires: List<String> = emptyList()
) : ConfigSerializable {

    constructor(me: Map<String, Any>) : this(
        me["name"] as String,
        me["version"] as String,
        me["main"] as String,
        me["description"] as? String,
        (me["requires"] as? List<*>)?.map { it.toString() } ?: emptyList()
    )

    companion object {
        private val BASE: ExtensionInfo = ExtensionInfo(
            "name", "version", "mainClass", "optional description", listOf("optional.required.ClassName")
        )

        @JvmStatic
        @Throws(Exception::class)
        fun of(path: Path, extensionName: String): ExtensionInfo {
            JarFile(path.toFile()).use { jar ->
                val entry = jar.getJarEntry("$extensionName.yml") ?: throw ExtensionNotFoundException(
                    extensionName, path.toString(),
                    YamlUtils.yaml().dumpAsMap(BASE.toMap())
                )

                InputStreamReader(jar.getInputStream(entry)).use { reader ->
                    return ExtensionInfo(YamlUtils.yaml().loadAs(reader, Map::class.java))
                }
            }
        }

        /**
         * Reads [extensionName].yml from [loader]'s resources — used when the addon JAR is loaded
         * in-memory (no [Path] on disk). Falls back to [of(Path, String)] for the on-disk path.
         */
        @JvmStatic
        @Throws(Exception::class)
        fun of(loader: ClassLoader, extensionName: String): ExtensionInfo {
            val stream = loader.getResourceAsStream("$extensionName.yml")
                ?: throw ExtensionNotFoundException(
                    extensionName, "<in-memory classloader>", YamlUtils.yaml().dumpAsMap(BASE.toMap())
                )
            return stream.use { InputStreamReader(it).use { reader -> ExtensionInfo(YamlUtils.yaml().loadAs(reader, Map::class.java)) } }
        }
    }

    fun name(): String = name
    fun version(): String = version
    fun main(): String = main
    fun description(): String? = description
    fun requires(): List<String> = List(requires.size) { requires[it] }

    /**
     * Which of [requires] cannot be resolved from [loader]. Empty means the extension is safe to load.
     *
     * Resolved without initializing: presence is the question, and running a third-party mod's static
     * initializers just to answer it is a side effect nobody asked for.
     */
    fun missingRequirements(loader: ClassLoader): List<String> = requires.filter { className ->
        try {
            Class.forName(className, false, loader)
            false
        } catch (_: Throwable) {
            // ClassNotFoundException is the expected miss; LinkageError and friends mean the class is
            // there but unusable, which for our purposes is the same answer.
            true
        }
    }

    override fun toString(): String {
        return "$name-$version (main: $main, description: ${description ?: "none"})"
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "version" to version,
        "main" to main,
        "description" to description,
        "requires" to requires
    )

    override fun serialize(section: ConfigurationSection) {
        toMap().forEach { (k, v) -> section.set(k, v) }
    }
}
