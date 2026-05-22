package dev.sweety.versioning.client.http;

import com.google.gson.JsonObject;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Read-only {@link IReleaseService} backed by HTTP endpoints on the update-server
 * ({@code GET /release/latest}, {@code GET /release/base-jar}). Non-blank {@code apiKey} is required
 * for {@link #resolveBaseJar}; it must match server setting {@code RELEASE_API_KEY} (JSON or env) and is sent
 * as header {@code X-Sweety-Release-Key}.
 */
public final class HttpCachingReleaseService implements IReleaseService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUri;
    private final String apiKey;
    private final Path cacheDir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public HttpCachingReleaseService(URI baseUri, String apiKey, Path cacheDir) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.apiKey = apiKey != null ? apiKey : "";
        this.cacheDir = Objects.requireNonNull(cacheDir, "cacheDir");
    }

    @Override
    public @Nullable ReleaseInfo latest(@NotNull Artifact artifact, @NotNull Channel channel) {
        try {
            URI uri = baseUri.resolve("/release/latest?artifact=" + urlEnc(artifact.name())
                    + "&channel=" + urlEnc(channel.name()));
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return null;
            }
            if (res.statusCode() != 200) {
                throw new IOException("latest HTTP " + res.statusCode() + ": " + res.body());
            }
            return parseReleaseJson(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException(e));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @NotNull Collection<ReleaseInfo> history(@NotNull Artifact artifact, @NotNull Channel channel) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull Path resolveBaseJar(@NotNull Artifact artifact, @NotNull Channel channel, @NotNull Version version) throws IOException {
        if (apiKey.isEmpty()) {
            throw new IOException("HttpCachingReleaseService: apiKey is required for resolveBaseJar (set RELEASE_API_KEY / server RELEASE_API_KEY)");
        }
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(safeCacheName(artifact, channel, version));
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            return target;
        }
        URI uri = baseUri.resolve("/release/base-jar?artifact=" + urlEnc(artifact.name())
                + "&channel=" + urlEnc(channel.name())
                + "&version=" + urlEnc(version.toString()));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("X-Sweety-Release-Key", apiKey)
                .GET()
                .build();
        Path tmp = Files.createTempFile(cacheDir, "dl-", ".jar.partial");
        try {
            HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200) {
                Files.deleteIfExists(res.body());
                throw new IOException("base-jar HTTP " + res.statusCode());
            }
            Files.move(res.body(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public @Nullable ReleaseInfo rollback(@NotNull Artifact artifact, @NotNull Channel channel) {
        throw new UnsupportedOperationException("HttpCachingReleaseService is read-only");
    }

    @Override
    public @Nullable ReleaseInfo updateRollout(@NotNull Artifact artifact, @NotNull Channel channel, float rollout) {
        throw new UnsupportedOperationException("HttpCachingReleaseService is read-only");
    }

    @Override
    public @Nullable ReleaseInfo applyRelease(
            @NotNull Artifact artifact,
            @NotNull Channel channel,
            @Nullable Version version,
            @Nullable Float rollout,
            @Nullable byte[] jar) {
        throw new UnsupportedOperationException("HttpCachingReleaseService is read-only");
    }

    @Override
    public @NotNull ReleaseInfo resolveLatest(@NotNull Artifact artifact, @NotNull Channel channel) {
        ReleaseInfo best = null;
        for (Channel ch : Channel.values()) {
            if (!channel.accepts(ch)) {
                continue;
            }
            ReleaseInfo candidate = latest(artifact, ch);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.updatedAt().isAfter(best.updatedAt())) {
                best = candidate;
            }
        }
        if (best != null) {
            return best;
        }
        ReleaseInfo fallback = latest(artifact, channel);
        return fallback != null ? fallback : ReleaseInfo.DEFAULT(channel);
    }

    private static String urlEnc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String safeCacheName(Artifact artifact, Channel channel, Version version) {
        return artifact.name() + "-" + channel.name() + "-" + version + ".jar";
    }

    public static ReleaseInfo parseReleaseJson(String json) {
        JsonObject obj = Utils.gson().fromJson(json, JsonObject.class);
        Version version = Version.parse(obj.get("version").getAsString());
        Channel ch = Channel.valueOf(obj.get("channel").getAsString().toUpperCase());
        float rollout;
        var rolloutEl = obj.get("rollout");
        if (rolloutEl == null || rolloutEl.isJsonNull()) {
            rollout = 1f;
        } else {
            rollout = rolloutEl.getAsFloat();
        }
        Instant updatedAt = Instant.parse(obj.get("updatedAt").getAsString());
        return new ReleaseInfo(version, ch, rollout, updatedAt);
    }
}
