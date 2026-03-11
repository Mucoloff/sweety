package dev.sweety.versioning.server.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Storage {

    private final Path root, base, cache, tmp, metadata;

    public Storage() throws IOException {
        this.root = Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "storage"));

        Files.createDirectories(this.base = this.root.resolve("base"));
        Files.createDirectories(this.cache = this.root.resolve("cache"));
        Files.createDirectories(this.tmp = this.root.resolve("tmp"));

        this.metadata = this.base.resolve("releases.json");
    }

    public Path root() {
        return this.root;
    }

    public Path base() {
        return this.base;
    }

    public Path cache() {
        return this.cache;
    }

    public Path tmp() {
        return this.tmp;
    }

    public Path metadata() {
        return this.metadata;
    }
}
