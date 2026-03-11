package dev.sweety.versioning.server.release;

import com.google.gson.JsonObject;
import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

public class ReleaseManager {

    private final Storage storage;
    private final Path baseStorageDir;
    private final Path metadataFile;

    private final HttpClient http = HttpClient.newBuilder().build(); //

    private final Deque<LatestInfo> history = new ArrayDeque<>();

    private final AtomicReference<LatestInfo> latest = new AtomicReference<>();

    public ReleaseManager(final Storage storage) throws IOException {
        this.storage = storage;
        this.baseStorageDir = storage.base();
        this.metadataFile = storage.metadata();
        update(loadOrDefault());
    }

    public @Nullable LatestInfo latest() {
        return latest.get();
    }

    public @NotNull Version latestApp() {
        final LatestInfo latest = latest();
        if (latest == null) return Version.ZERO;
        return latest.app();
    }

    public @NotNull Version latestLauncher() {
        final LatestInfo latest = latest();
        if (latest == null) return Version.ZERO;
        return latest.launcher();
    }

    public void update(@NotNull LatestInfo latest) {
        this.latest.set(latest);
    }

    public void update(@NotNull Version launcher, @NotNull Version app) {
        update(new LatestInfo(launcher, app, Instant.now()));
    }

    public void updateApp(@NotNull Version app) {
        update(latestLauncher(), app);
    }

    public void updateLauncher(@NotNull Version launcher) {
        update(launcher, latestApp());
    }

    private LatestInfo loadOrDefault() throws IOException {
        if (!Files.exists(metadataFile)) {
            LatestInfo def = LatestInfo.DEFAULT;
            persist(def);
            return def;
        }

        JsonObject root = Utils.GSON.fromJson(Files.readString(metadataFile), JsonObject.class);
        JsonObject cur = root.getAsJsonObject("latest");
        if (cur == null) {
            return LatestInfo.DEFAULT;
        }

        return new LatestInfo(
                Version.parse(cur.get("launcher").getAsString()),
                Version.parse(cur.get("app").getAsString()),
                Instant.parse(cur.get("updatedAt").getAsString())
        );
    }

    private void persist() throws IOException {
        LatestInfo state = latest();
        if (state == null) state = LatestInfo.DEFAULT;
        persist(state);
    }

    private void persist(LatestInfo state) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject cur = new JsonObject();
        cur.addProperty("launcher", state.launcher().toString());
        cur.addProperty("app", state.launcher().toString());
        cur.addProperty("updatedAt", state.updatedAt().toString());
        root.add("latest", cur);

        Path tmp = metadataFile.resolveSibling(metadataFile.getFileName() + ".tmp");
        Files.writeString(tmp, Utils.GSON.toJson(root));
        Files.move(tmp, metadataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path resolveBaseJar(Artifact artifact, Version version) {
        return baseStorageDir.resolve(artifact + "-" + version + ".jar");
    }

    public synchronized boolean rollback() throws IOException {
        final LatestInfo prev = history.pollFirst();
        if (prev == null) return false;
        update(prev);
        persist();
        return true;
    }
}
