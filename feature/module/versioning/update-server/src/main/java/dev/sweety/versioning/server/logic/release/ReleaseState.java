package dev.sweety.versioning.server.logic.release;

import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.domain.release.ReleaseState} directly.
 */
@Deprecated
public class ReleaseState extends dev.sweety.versioning.server.domain.release.ReleaseState {

    public ReleaseState(Artifact artifact, Storage storage) throws IOException {
        super(storage.resolveMetadataPath(artifact), storage.resolveArtifactPath(artifact));
    }
}
