package dev.sweety.versioning.server.release;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.versioning.server.storage.Storage;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.LatestInfo;
import dev.sweety.versioning.version.Version;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ReleaseManager {

    private static final int HISTORY_LIMIT = 20;
    private final Path baseStorageDir;
    private final Path metadataFile;
    private final Path tempDir;

    private final Deque<LatestInfo> history = new ArrayDeque<>();

    private final AtomicReference<LatestInfo> latest = new AtomicReference<>();

    public ReleaseManager(final Storage storage) throws IOException {
        this.baseStorageDir = storage.base();
        this.metadataFile = storage.metadata();
        this.tempDir = storage.tmp();
        update(loadOrDefault());
    }

    public @NotNull LatestInfo latest() {
        return latest.get();
    }

    public void update(@NotNull LatestInfo latest) {
        this.latest.set(latest);
    }

    private LatestInfo loadOrDefault() throws IOException {
        if (!Files.exists(metadataFile)) {
            LatestInfo def = LatestInfo.DEFAULT;
            persist(def);
            return def;
        }

        final JsonObject root = Utils.GSON.fromJson(Files.readString(metadataFile), JsonObject.class);
        final JsonObject latest = root.getAsJsonObject("latest");
        final JsonArray hist = root.getAsJsonArray("history");

        if (hist != null) {
            for (JsonElement el : hist) {
                JsonObject obj = el.getAsJsonObject();
                history.addLast(deserialize(obj));
            }
        }

        return deserialize(latest);

    }

    private void persist() throws IOException {
        persist(latest());
    }

    private void persist(LatestInfo state) throws IOException {
        final JsonObject root = new JsonObject();

        root.add("latest", serialize(state));

        final JsonArray historyArray = new JsonArray();

        this.history.stream().map(this::serialize).forEach(historyArray::add);

        root.add("history", historyArray);

        Path tmp = this.tempDir.resolve(this.metadataFile.getFileName() + ".tmp");
        Files.writeString(tmp, Utils.GSON.toJson(root));
        Files.move(tmp, this.metadataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private JsonObject serialize(LatestInfo state) {
        JsonObject obj = new JsonObject();

        obj.addProperty("launcher", state.launcher().toString());
        obj.addProperty("app", state.app().toString());
        obj.addProperty("updatedAt", state.updatedAt().toString());

        return obj;
    }

    private LatestInfo deserialize(JsonObject obj) {
        return new LatestInfo(
                Version.parse(obj.get("launcher").getAsString()),
                Version.parse(obj.get("app").getAsString()),
                Instant.parse(obj.get("updatedAt").getAsString())
        );
    }

    private Path resolveFile(Path path, Artifact artifact, Version version, String extension) throws IOException {
        final String name = artifact.name().toLowerCase();
        final Path dir = version.resolve(path.resolve(name));
        Files.createDirectories(dir);
        return dir.resolve(name + "-" + version + "." + extension);
    }

    private Path resolveTempJar(Artifact artifact, Version version) throws IOException {
        return resolveFile(this.tempDir, artifact, version, "jar.tmp");
    }

    public Path resolveBaseJar(Artifact artifact, Version version) throws IOException {
        return resolveFile(this.baseStorageDir, artifact, version, "jar");
    }

    public synchronized boolean rollback() throws IOException {
        final LatestInfo prev = history.pollFirst();
        if (prev == null) return false;
        update(prev);
        persist();
        return true;
    }

    public synchronized boolean applyRelease(
            String launcherVersion,
            byte[] launcherJar,
            String appVersion,
            byte[] appJar
    ) throws IOException {
        Version launcher = null;
        Version app = null;

        if (launcherVersion != null) {
            if (launcherJar == null) {
                throw new IllegalArgumentException("launcherJar missing");
            }

            launcher = Version.parse(launcherVersion);

            writeJar(Artifact.LAUNCHER, launcher, launcherJar);
        }

        if (appVersion != null) {
            if (appJar == null) {
                throw new IllegalArgumentException("appJar missing");
            }

            app = Version.parse(appVersion);
            writeJar(Artifact.APP, app, appJar);
        }

        LatestInfo current = latest();

        Version nextLauncher = launcher != null ? launcher : current.launcher();
        Version nextApp = app != null ? app : current.app();

        LatestInfo next = new LatestInfo(
                nextLauncher,
                nextApp
        );

        if (Objects.equals(next, current)) return false;

        this.history.addFirst(current);
        while (this.history.size() > HISTORY_LIMIT) this.history.removeLast();

        update(next);
        persist();

        System.out.println("Release applied: launcher=" + nextLauncher + " app=" + nextApp);

        return true;
    }

    private void writeJar(Artifact artifact, Version version, byte[] data) throws IOException {
        Path temp = resolveTempJar(artifact, version), target = resolveBaseJar(artifact, version);
        Files.write(temp, data);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

    }
}
