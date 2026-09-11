package dev.sweety.versioning.server.data

import dev.sweety.versioning.version.Version
import dev.sweety.versioning.version.artifact.Artifact
import dev.sweety.versioning.version.channel.Channel
import java.nio.file.Path
import java.util.UUID

open class CacheKey(
    open val artifact: Artifact,
    open val channel: Channel,
    open val version: Version,
    open val clientId: UUID
) {
    fun artifact(): Artifact = artifact
    fun channel(): Channel = channel
    fun version(): Version = version
    fun clientId(): UUID = clientId

    open fun resolve(artifactRoot: Path): Path {
        val channelDir = artifactRoot.resolve(channel.prettyName())
        val versionDir = version.resolve(channelDir)
        return versionDir.resolve("patch").resolve("cache").resolve(clientId.toString())
    }

    open fun toPath(root: Path, extension: String): Path {
        return resolve(root).resolve(artifact.prettyName() + extension)
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
