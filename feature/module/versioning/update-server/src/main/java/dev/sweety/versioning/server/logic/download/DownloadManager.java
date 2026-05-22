package dev.sweety.versioning.server.logic.download;

import dev.sweety.versioning.server.adapter.out.token.InMemoryDownloadTokenStore;
import dev.sweety.versioning.server.domain.download.Token;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link InMemoryDownloadTokenStore} directly.
 */
@Deprecated
public class DownloadManager extends InMemoryDownloadTokenStore {

    /** @deprecated use {@link #consume(String)} */
    @Deprecated
    public @Nullable Token search(String tokenId) {
        return consume(tokenId);
    }
}
