package dev.sweety.versioning.server.logic.download;

import dev.sweety.versioning.server.adapter.out.cache.CacheManager;
import dev.sweety.versioning.server.application.patch.PatchManager;
import dev.sweety.versioning.server.domain.client.ClientRegistry;
import dev.sweety.versioning.server.port.out.DownloadTokenStore;
import dev.sweety.versioning.version.IReleaseService;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.adapter.in.http.DownloadHandler} directly.
 */
@Deprecated
public class DownloadHandler extends dev.sweety.versioning.server.adapter.in.http.DownloadHandler {

    public DownloadHandler(DownloadTokenStore downloadManager, CacheManager cacheManager, ClientRegistry clientRegistry, IReleaseService releaseManager, PatchManager patchManager) {
        super(downloadManager, cacheManager, clientRegistry, releaseManager, patchManager);
    }
}
