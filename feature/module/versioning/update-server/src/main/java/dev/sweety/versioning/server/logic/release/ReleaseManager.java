package dev.sweety.versioning.server.logic.release;

import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.server.port.out.ReleaseRepository;

import java.io.IOException;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.application.release.ReleaseManager} directly.
 */
@Deprecated
public class ReleaseManager extends dev.sweety.versioning.server.application.release.ReleaseManager {

    public ReleaseManager(Storage storage, ReleaseRepository repository) throws IOException {
        super(storage, repository);
    }
}
