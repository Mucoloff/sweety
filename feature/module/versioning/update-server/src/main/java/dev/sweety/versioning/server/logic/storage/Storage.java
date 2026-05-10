package dev.sweety.versioning.server.logic.storage;

import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Storage {

    private final Path root, settings;
    private final Map<Artifact, Path> pathCache = new ConcurrentHashMap<>();

    public Storage() throws IOException {
        this.root = Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "storage"));
        Files.createDirectories(this.root);
        this.settings = this.root.resolve("settings.json");
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

    public static Path temp(Path path){
        return path.resolveSibling(path.getFileName() + ".tmp");
    }
}
