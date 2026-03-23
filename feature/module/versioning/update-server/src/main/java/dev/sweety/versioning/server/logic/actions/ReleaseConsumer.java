package dev.sweety.versioning.server.logic.actions;

import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;

public interface ReleaseConsumer {

    void release(Artifact artifact, ReleaseInfo releaseInfo);

}
