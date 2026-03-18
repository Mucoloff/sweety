package dev.sweety.versioning.server.logic.release;

import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;

public interface ReleaseConsumer {

    void release(Artifact artifact, ReleaseInfo releaseInfo);

}
