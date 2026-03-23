package dev.sweety.versioning.server.logic.decision;

import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.version.Version;

public record UpdateDecision(
        boolean update,
        Version targetVersion,
        DownloadType downloadType,
        boolean forced
) {}