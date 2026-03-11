package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public class ReleaseManager {

    public record VersionState(String launcherVersion, String appVersion, Instant updatedAt) {
    }

    public record ReleaseUpdate(String launcherVersion, String appVersion, String launcherUrl, String appUrl) {
    }

    private static final int HISTORY_LIMIT = 20;

    private final Path baseStorageDir;
    private final Path metadataFile;
    private final Gson gson;
    private final HttpClient http;

    private final Deque<VersionState> history = new ArrayDeque<>();
    private volatile VersionState current;

    public ReleaseManager(Path baseStorageDir) throws Exception {
        this.baseStorageDir = Objects.requireNonNull(baseStorageDir, "baseStorageDir");
        this.metadataFile = this.baseStorageDir.resolve("releases.json");
        this.gson = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        this.http = HttpClient.newHttpClient();
        Files.createDirectories(baseStorageDir);
        this.current = loadOrDefault();
    }

    public VersionState current() {
        return current;
    }

    public synchronized boolean applyRelease(JsonObject payload) throws Exception {
        ReleaseUpdate update = parseReleaseUpdate(payload, current);
        return applyRelease(update);
    }

    public synchronized boolean applyRelease(ReleaseUpdate update) throws Exception {
        String launcherVersion = update.launcherVersion();
        String appVersion = update.appVersion();
        String launcherUrl = update.launcherUrl();
        String appUrl = update.appUrl();

        if (appUrl != null && !appUrl.isBlank()) {
            downloadToBase("app", appVersion, URI.create(appUrl));
        }
        if (launcherUrl != null && !launcherUrl.isBlank()) {
            downloadToBase("launcher", launcherVersion, URI.create(launcherUrl));
        }

        VersionState next = new VersionState(launcherVersion, appVersion, Instant.now());
        if (!Objects.equals(next, current)) {
            history.addFirst(current);
            while (history.size() > HISTORY_LIMIT) {
                history.removeLast();
            }
            current = next;
            persist();
            System.out.println("Release applied: launcher=" + launcherVersion + " app=" + appVersion);
            return true;
        }
        return false;
    }

    public synchronized boolean rollback() throws Exception {
        VersionState prev = history.pollFirst();
        if (prev == null) {
            return false;
        }
        current = prev;
        persist();
        return true;
    }

    public Path resolveBaseJar(String artifact, String version) {
        return baseStorageDir.resolve(artifact + "-" + version + ".jar");
    }

    private void downloadToBase(String artifact, String version, URI url) throws Exception {
        Path target = resolveBaseJar(artifact, version);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        HttpRequest req = HttpRequest.newBuilder(url).GET().build();
        try {
            HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IOException("Download failed " + url + " status=" + res.statusCode());
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private VersionState loadOrDefault() throws Exception {
        if (!Files.exists(metadataFile)) {
            VersionState def = new VersionState("1.0.0", "1.0.0", Instant.now());
            persist(def);
            return def;
        }
        JsonObject root = gson.fromJson(Files.readString(metadataFile), JsonObject.class);
        JsonObject cur = root.getAsJsonObject("current");
        if (cur == null) {
            return new VersionState("1.0.0", "1.0.0", Instant.now());
        }
        return new VersionState(
                cur.get("launcher").getAsString(),
                cur.get("app").getAsString(),
                Instant.parse(cur.get("updatedAt").getAsString())
        );
    }

    private void persist() throws Exception {
        persist(current);
    }

    private void persist(VersionState state) throws Exception {
        JsonObject root = new JsonObject();
        JsonObject cur = new JsonObject();
        cur.addProperty("launcher", state.launcherVersion());
        cur.addProperty("app", state.appVersion());
        cur.addProperty("updatedAt", state.updatedAt().toString());
        root.add("current", cur);

        Path tmp = metadataFile.resolveSibling(metadataFile.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(root));
        Files.move(tmp, metadataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static ReleaseUpdate parseReleaseUpdate(JsonObject payload, VersionState fallback) {
        ReleaseUpdate fallbackUpdate = new ReleaseUpdate(fallback.launcherVersion(), fallback.appVersion(), null, null);
        if (payload == null) return fallbackUpdate;

        if (payload.has("release") && payload.get("release").isJsonObject()) {
            JsonObject release = payload.getAsJsonObject("release");
            String tag = firstNonBlank(release, "tag_name", null);
            String versionFromTag = normalizeTag(tag);

            String launcherVersion = fallback.launcherVersion();
            String appVersion = fallback.appVersion();
            String launcherUrl = null;
            String appUrl = null;

            if (release.has("assets") && release.get("assets").isJsonArray()) {
                for (var e : release.getAsJsonArray("assets")) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject asset = e.getAsJsonObject();
                    String name = firstNonBlank(asset, "name", "");
                    String downloadUrl = firstNonBlank(asset, "browser_download_url", null);
                    String lower = name.toLowerCase();

                    if (downloadUrl == null || downloadUrl.isBlank()) {
                        continue;
                    }
                    if (lower.contains("launcher") && lower.endsWith(".jar")) {
                        launcherUrl = downloadUrl;
                        launcherVersion = extractVersionFromAsset(name, versionFromTag, fallback.launcherVersion());
                    } else if (lower.contains("app") && lower.endsWith(".jar")) {
                        appUrl = downloadUrl;
                        appVersion = extractVersionFromAsset(name, versionFromTag, fallback.appVersion());
                    }
                }
            }

            if (launcherUrl == null && appUrl == null) return fallbackUpdate;
            return new ReleaseUpdate(launcherVersion, appVersion, launcherUrl, appUrl);
        }

        String launcherVersion = firstNonBlank(payload, "launcher", fallback.launcherVersion());
        String appVersion = firstNonBlank(payload, "app", fallback.appVersion());
        String launcherUrl = firstNonBlank(payload, "launcherUrl", null);
        String appUrl = firstNonBlank(payload, "appUrl", null);
        return new ReleaseUpdate(launcherVersion, appVersion, launcherUrl, appUrl);
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String t = tag.trim();
        return (t.startsWith("v") || t.startsWith("V")) ? t.substring(1) : t;
    }

    private static String extractVersionFromAsset(String assetName, String tagVersion, String fallback) {
        if (assetName != null) {
            int dash = assetName.indexOf('-');
            int dotJar = assetName.toLowerCase().lastIndexOf(".jar");
            if (dash >= 0 && dotJar > dash + 1) {
                String v = assetName.substring(dash + 1, dotJar).trim();
                if (!v.isBlank()) {
                    return v;
                }
            }
        }
        if (tagVersion != null && !tagVersion.isBlank()) {
            return tagVersion;
        }
        return fallback;
    }

    private static String firstNonBlank(JsonObject payload, String key, String fallback) {
        if (payload != null && payload.has(key) && !payload.get(key).isJsonNull()) {
            String s = payload.get(key).getAsString();
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return fallback;
    }

    private static URI safeUri(String raw) {
        try {
            return new URI(raw);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid bootstrap URL: " + raw, e);
        }
    }
}

