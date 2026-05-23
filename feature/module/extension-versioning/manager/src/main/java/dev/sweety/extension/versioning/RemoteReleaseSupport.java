package dev.sweety.extension.versioning;

import dev.sweety.versioning.client.http.HttpCachingReleaseService;
import dev.sweety.versioning.client.http.HttpTokenDownloadReleaseService;
import dev.sweety.versioning.version.ReleaseService;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Factory for remote {@link ReleaseService} instances used with {@link ExtensionUpdater}.
 * <p>
 * Environment variables for {@link #fromEnvironment(Path)}:
 * <ul>
 *   <li>{@code UPDATE_HTTP_BASE} — HTTP root of the update server (e.g. {@code http://localhost:8080})</li>
 *   <li>{@code RELEASE_API_KEY} — must match server {@link dev.sweety.versioning.server.Settings#RELEASE_API_KEY}</li>
 * </ul>
 */
public final class RemoteReleaseSupport {

    public static final String ENV_UPDATE_HTTP_BASE = "UPDATE_HTTP_BASE";
    public static final String ENV_RELEASE_API_KEY = "RELEASE_API_KEY";

    private RemoteReleaseSupport() {}

    /**
     * Uses {@code GET /release/base-jar} (static API key). Prefer {@link #httpWithTokenDownload} for parity with {@code /download}.
     */
    public static ReleaseService http(URI httpBase, String releaseApiKey, Path cacheDir) {
        return new HttpCachingReleaseService(normalizeBase(Objects.requireNonNull(httpBase, "httpBase")),
                releaseApiKey != null ? releaseApiKey : "",
                Objects.requireNonNull(cacheDir, "cacheDir"));
    }

    /**
     * Uses {@code POST /release/download-token} then {@code GET /download} (single-use token), same as the launcher path.
     */
    public static ReleaseService httpWithTokenDownload(URI httpBase, String releaseApiKey, UUID clientId, Path cacheDir) {
        return new HttpTokenDownloadReleaseService(normalizeBase(Objects.requireNonNull(httpBase, "httpBase")),
                releaseApiKey != null ? releaseApiKey : "",
                Objects.requireNonNull(clientId, "clientId"),
                Objects.requireNonNull(cacheDir, "cacheDir"));
    }

    /**
     * {@link #http} from {@link #ENV_UPDATE_HTTP_BASE} and {@link #ENV_RELEASE_API_KEY}.
     */
    public static ReleaseService fromEnvironment(Path cacheDir) {
        EnvConfig cfg = parseEnv();
        return http(cfg.base(), cfg.key(), cacheDir);
    }

    /**
     * {@link #httpWithTokenDownload} from env plus an explicit {@code clientId} (must match the identity used for downloads).
     */
    public static ReleaseService fromEnvironmentWithTokenDownload(Path cacheDir, UUID clientId) {
        EnvConfig cfg = parseEnv();
        return httpWithTokenDownload(cfg.base(), cfg.key(), clientId, cacheDir);
    }

    private record EnvConfig(URI base, String key) {}

    private static EnvConfig parseEnv() {
        String base = System.getenv(ENV_UPDATE_HTTP_BASE);
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("Set env " + ENV_UPDATE_HTTP_BASE + " (e.g. http://localhost:8080)");
        }
        String key = System.getenv(ENV_RELEASE_API_KEY);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set env " + ENV_RELEASE_API_KEY + " to match the update-server release API key");
        }
        return new EnvConfig(URI.create(base.trim()), key);
    }

    static URI normalizeBase(URI base) {
        String s = base.toString();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return URI.create(s);
    }
}
