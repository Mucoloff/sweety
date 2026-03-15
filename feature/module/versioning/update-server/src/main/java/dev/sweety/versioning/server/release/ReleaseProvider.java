package dev.sweety.versioning.server.release;

import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;

import java.io.IOException;

interface ReleaseProvider {
    ReleaseInfo provide(Artifact artifact) throws IOException;
}
