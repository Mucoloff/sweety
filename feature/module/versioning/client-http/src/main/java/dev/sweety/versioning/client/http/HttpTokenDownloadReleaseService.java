package dev.sweety.versioning.client.http;

import com.google.gson.JsonObject;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only {@link ReleaseService}: {@code latest} via {@link HttpCachingReleaseService};
 * {@code resolveBaseJar} reserves a single-use token ({@code POST /release/download-token}) then downloads via {@code GET /download}.
 */
public final class HttpTokenDownloadReleaseService implements ReleaseService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpCachingReleaseService meta;
    private final URI baseUri;
    private final String apiKey;
    private final UUID clientId;
    private final Path cacheDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public HttpTokenDownloadReleaseService(URI baseUri, String apiKey, UUID clientId, Path cacheDir) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.apiKey = apiKey != null ? apiKey : "";
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir");
        this.meta = new HttpCachingReleaseService(baseUri, "", cacheDir);
    }

    @Override
    public @Nullable ReleaseInfo latest(@NotNull Artifact artifact, @NotNull Channel channel) {
        return meta.latest(artifact, channel);
    }

    @Override
    public @NotNull Collection<ReleaseInfo> history(@NotNull Artifact artifact, @NotNull Channel channel) {
        return meta.history(artifact, channel);
    }

    @Override
    public @NotNull Path resolveBaseJar(@NotNull Artifact artifact, @NotNull Channel channel, @NotNull Version version)
            throws IOException {
        if (apiKey.isEmpty()) {
            throw new IOException("HttpTokenDownloadReleaseService: apiKey is required (RELEASE_API_KEY)");
        }
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(cacheName(artifact, channel, version));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            return target;
        }

        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId.toString());
        body.addProperty("artifact", artifact.name());
        body.addProperty("channel", channel.name());
        body.addProperty("version", version.toString());
        body.addProperty("from", Version.ZERO.toString());
        body.addProperty("downloadType", DownloadType.FULL.name());

        URI reserve = baseUri.resolve("/release/download-token");
        HttpRequest post = HttpRequest.newBuilder(reserve)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Sweety-Release-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(Utils.gson().toJson(body), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = http.send(post, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IOException("download-token HTTP " + res.statusCode() + ": " + res.body());
            }
            JsonObject obj = Utils.gson().fromJson(res.body(), JsonObject.class);
            String downloadPath = obj.get("downloadPath").getAsString();
            URI getJar = baseUri.resolve(downloadPath);

            Path tmp = Files.createTempFile(cacheDir, "tok-", ".jar.partial");
            try {
                HttpRequest get = HttpRequest.newBuilder(getJar).timeout(TIMEOUT).GET().build();
                HttpResponse<Path> dl = http.send(get, HttpResponse.BodyHandlers.ofFile(tmp));
                if (dl.statusCode() != 200) {
                    Files.deleteIfExists(dl.body());
                    throw new IOException("download HTTP " + dl.statusCode());
                }
                Files.move(dl.body(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public @Nullable ReleaseInfo rollback(@NotNull Artifact artifact, @NotNull Channel channel) {
        return meta.rollback(artifact, channel);
    }

    @Override
    public @Nullable ReleaseInfo updateRollout(@NotNull Artifact artifact, @NotNull Channel channel, float rollout) {
        return meta.updateRollout(artifact, channel, rollout);
    }

    @Override
    public @Nullable ReleaseInfo applyRelease(
            @NotNull Artifact artifact,
            @NotNull Channel channel,
            @Nullable Version version,
            @Nullable Float rollout,
            @Nullable byte[] jar) {
        return meta.applyRelease(artifact, channel, version, rollout, jar);
    }

    @Override
    public @NotNull ReleaseInfo resolveLatest(@NotNull Artifact artifact, @NotNull Channel channel) {
        return meta.resolveLatest(artifact, channel);
    }

    private static String cacheName(Artifact artifact, Channel channel, Version version) {
        return artifact.name() + "-" + channel.name() + "-" + version + ".jar";
    }
}
