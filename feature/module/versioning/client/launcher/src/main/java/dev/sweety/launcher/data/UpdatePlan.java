package dev.sweety.launcher.data;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;

/**
 * Value object describing a planned update for a given artifact.
 */
public record UpdatePlan(
        Artifact artifact,
        Version fromVersion,
        Version toVersion,
        String token,
        DownloadType downloadType
) {
}
