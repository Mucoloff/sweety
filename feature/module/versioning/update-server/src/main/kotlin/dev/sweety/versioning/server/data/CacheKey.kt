package dev.sweety.versioning.server.data

import dev.sweety.versioning.version.Version
import dev.sweety.versioning.version.artifact.Artifact
import dev.sweety.versioning.version.channel.Channel
import java.nio.file.Path
import java.util.UUID

open class CacheKey @JvmOverloads constructor(
    open val artifact: Artifact,
    open val channel: Channel,
    open val version: Version,
    open val clientId: UUID? = null
) {
    fun artifact(): Artifact = artifact
    fun channel(): Channel = channel
    fun version(): Version = version
    fun clientId(): UUID? = clientId

    open fun fileName(extension: String = ".jar"): String =
        "${artifact.prettyName().lowercase()}-${version}$extension"

    open fun resolve(artifactRoot: Path): Path {
        val channelDir = artifactRoot.resolve(channel.prettyName())
        val versionDir = version.resolve(channelDir)
        return if (clientId != null) {
            versionDir.resolve("patch").resolve("cache").resolve(clientId.toString())
        } else {
            versionDir
        }
    }

    open fun toPath(root: Path, extension: String): Path {
        return if (clientId != null) {
            resolve(root).resolve(artifact.prettyName() + extension)
        } else {
            root.resolve(fileName(extension)).normalize()
        }
    }

    open fun toPath(root: Path): Path {
        return toPath(root, ".jar")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheKey) return false
        return artifact == other.artifact && channel == other.channel && version == other.version && clientId == other.clientId
    }

    override fun hashCode(): Int {
        var result = artifact.hashCode()
        result = 31 * result + channel.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + clientId.hashCode()
        return result
    }

    override fun toString(): String =
        "CacheKey(artifact=$artifact, channel=$channel, version=$version, clientId=$clientId)"
}
