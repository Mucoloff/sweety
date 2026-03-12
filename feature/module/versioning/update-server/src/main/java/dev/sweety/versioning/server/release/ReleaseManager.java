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

    private final Deque<LatestInfo> history = new ArrayDeque<>();

    private final AtomicReference<LatestInfo> latest = new AtomicReference<>();

    public ReleaseManager(final Storage storage) throws IOException {
        this.baseStorageDir = storage.base();
        this.metadataFile = storage.metadata();
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
        persist(latest());
    }

    private void persist(LatestInfo state) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject cur = new JsonObject();
        cur.addProperty("launcher", state.launcher().toString());
        cur.addProperty("app", state.app().toString());
        cur.addProperty("updatedAt", state.updatedAt().toString());
        root.add("latest", cur);

        Path tmp = metadataFile.resolveSibling(metadataFile.getFileName() + ".tmp");
        Files.writeString(tmp, Utils.GSON.toJson(root));
        Files.move(tmp, metadataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path resolveBaseJar(Artifact artifact, Version version) {
        final String name = artifact.name().toLowerCase();
        final String ver = version.toString();
        return baseStorageDir.resolve(name)
                .resolve(ver)
                .resolve(name + "-" + ver + ".jar");
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

            writeJar(resolveBaseJar(Artifact.LAUNCHER, launcher), launcherJar);
        }

        if (appVersion != null) {
            if (appJar == null) {
                throw new IllegalArgumentException("appJar missing");
            }

            app = Version.parse(appVersion);
            writeJar(resolveBaseJar(Artifact.APP, app), appJar);
        }

        LatestInfo current = latest();

        Version nextLauncher = launcher != null ? launcher : current.launcher();
        Version nextApp = app != null ? app : current.app();

        LatestInfo next = new LatestInfo(
                nextLauncher,
                nextApp
        );

        if (Objects.equals(next, current)) {
            return false;
        }

        history.addFirst(current);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }

        update(next);
        persist();

        System.out.println("Release applied: launcher=" + nextLauncher + " app=" + nextApp);

        return true;
    }

    private void writeJar(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        Files.write(tmp, data);

        Files.move(
                tmp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );

    }
}
