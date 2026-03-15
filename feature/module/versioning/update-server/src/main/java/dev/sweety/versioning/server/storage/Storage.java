package dev.sweety.versioning.server.storage;

import dev.sweety.versioning.version.Artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

public class Storage {

    private final Path root, settings, temp;

    private final EnumMap<Artifact, Path> metadata = new EnumMap<>(Artifact.class);
    private final EnumMap<Artifact, Path> artifacts = new EnumMap<>(Artifact.class);
    private final EnumMap<Artifact, Path> cache = new EnumMap<>(Artifact.class);
    private final EnumMap<Artifact, Path> tmp = new EnumMap<>(Artifact.class);

    public Storage() throws IOException {
        this.root = Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "storage"));

        Path base, cache;

        Files.createDirectories(base = this.root.resolve("base"));
        Files.createDirectories(cache = this.root.resolve("cache"));
        Files.createDirectories(temp = this.root.resolve("tmp"));

        for (Artifact value : Artifact.values()) {
            final Path basePath = base.resolve(value.name().toLowerCase());
            final Path cachePath = cache.resolve(value.name().toLowerCase());
            final Path tmpPath = temp.resolve(value.name().toLowerCase());

            Files.createDirectories(basePath);
            Files.createDirectories(cachePath);
            Files.createDirectories(tmpPath);

            this.artifacts.put(value, basePath);
            this.metadata.put(value, basePath.resolve("releases.json"));
            this.cache.put(value, cachePath);
            this.tmp.put(value, tmpPath);
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

    public EnumMap<Artifact, Path> cache() {
        return cache;
    }

    public EnumMap<Artifact, Path> tmp() {
        return tmp;
    }

    public Path settings() {
        return settings;
    }

    public Path temp() {
        return temp;
    }
}
