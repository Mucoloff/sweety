package dev.sweety.versioning.server.data

import dev.sweety.versioning.protocol.handshake.DownloadType
import dev.sweety.versioning.version.Version

/**
 * Extensible polymorphic representation of an update evaluation decision.
 */
open class UpdateDecision(
    open val update: Boolean,
    open val targetVersion: Version,
    open val downloadType: DownloadType?,
    open val forced: Boolean
) {
    // Java record compatibility accessors
    fun update(): Boolean = update
    fun targetVersion(): Version = targetVersion
    fun downloadType(): DownloadType? = downloadType
    fun forced(): Boolean = forced

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UpdateDecision) return false
        return update == other.update &&
                targetVersion == other.targetVersion &&
                downloadType == other.downloadType &&
                forced == other.forced
    }

    override fun hashCode(): Int {
        var result = update.hashCode()
        result = 31 * result + targetVersion.hashCode()
        result = 31 * result + (downloadType?.hashCode() ?: 0)
        result = 31 * result + forced.hashCode()
        return result
    }

    override fun toString(): String =
        "UpdateDecision(update=$update, targetVersion=$targetVersion, downloadType=$downloadType, forced=$forced)"

    companion object {
        @JvmStatic
        fun upToDate(current: Version): UpdateDecision =
            UpdateDecision(update = false, targetVersion = current, downloadType = null, forced = false)

        @JvmStatic
        @JvmOverloads
        fun upgrade(target: Version, downloadType: DownloadType, forced: Boolean = false): UpdateDecision =
            UpdateDecision(update = true, targetVersion = target, downloadType = downloadType, forced = forced)
    }
}
