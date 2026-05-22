package dev.sweety.versioning.server.logic.patch;

import dev.sweety.versioning.server.adapter.out.storage.Storage;
import dev.sweety.versioning.version.IReleaseService;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.application.patch.PatchManager} directly.
 */
@Deprecated
public class PatchManager extends dev.sweety.versioning.server.application.patch.PatchManager {

    public PatchManager(Storage storage, IReleaseService releaseManager) {
        super(storage, releaseManager);
    }
}
