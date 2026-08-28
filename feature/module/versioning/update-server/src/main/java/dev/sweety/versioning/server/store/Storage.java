package dev.sweety.versioning.server.store;

import dev.sweety.versioning.server.store.StoragePort;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Storage implements StoragePort {

    private final Path root, settings;
    private final Map<Artifact, Path> pathCache = new ConcurrentHashMap<>();

    public Storage(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(this.root);
        this.settings = this.root.resolve("settings.json");
    }

    public Storage() throws IOException {
        this(Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "storage")));
    }

    public Path root() {
        return this.root;
    }

    public Path resolveArtifactPath(Artifact artifact) {
        return pathCache.computeIfAbsent(artifact, a -> {
            try {
                Path path = this.root.resolve(a.name());
                Files.createDirectories(path);
                return path;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public Path resolveMetadataPath(Artifact artifact) {
        return resolveArtifactPath(artifact).resolve("releases.json");
    }

    public Path settings() {
        return settings;
    }

    public static Path temp(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }
}
