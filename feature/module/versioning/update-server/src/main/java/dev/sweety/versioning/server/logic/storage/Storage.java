package dev.sweety.versioning.server.logic.storage;

import dev.sweety.versioning.version.artifact.Artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public class Storage {

    private final Path root, settings;

    private final EnumMap<Artifact, Path> metadata = new EnumMap<>(Artifact.class);
    private final EnumMap<Artifact, Path> artifacts = new EnumMap<>(Artifact.class);

    public Storage() throws IOException {
        this.root = Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "run/storage"));

        for (Artifact value : Artifact.values()) {
            final String artifact = value.prettyName();
            final Path basePath = this.root.resolve(artifact);

            Files.createDirectories(basePath);

            this.artifacts.put(value, basePath);
            this.metadata.put(value, basePath.resolve("releases.json"));
        }

        this.settings = this.root.resolve("settings.json");
    }

    public Path root() {
        return this.root;
    }

    public EnumMap<Artifact, Path> metadata() {
        return metadata;
    }

    public EnumMap<Artifact, Path> artifacts() {
        return artifacts;
    }

    public Path settings() {
        return settings;
    }

    public static Path temp(Path path){
        return path.resolveSibling(path.getFileName() + ".tmp");
    }
}
