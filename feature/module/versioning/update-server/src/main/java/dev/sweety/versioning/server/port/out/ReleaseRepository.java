package dev.sweety.versioning.server.port.out;

import dev.sweety.versioning.server.domain.release.ReleaseState;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;

public interface ReleaseRepository {
    void load(Artifact artifact, ReleaseState state) throws IOException;
    void save(Artifact artifact, ReleaseState state) throws IOException;
}
