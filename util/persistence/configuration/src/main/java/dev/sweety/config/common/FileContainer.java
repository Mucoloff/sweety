package dev.sweety.config.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public abstract class FileContainer {

    protected final Path root;

    public FileContainer(Path root) {
        this.root = root;
    }

    public FileContainer(Path parent, String name, boolean file, Consumer<String> logger) {
        this.root = parent.resolve(name);
        try {
            if (!Files.exists(this.root)) {
                if (file) {
                    Path parentPath = this.root.getParent();
                    if (parentPath != null) {
                        Files.createDirectories(parentPath);
                    }
                    Files.createFile(this.root);
                } else {
                    Files.createDirectories(this.root);
                }
            }
        } catch (IOException e) {
            logger.accept("Failed to create file at " + this.root.toAbsolutePath() + ": " + e.getMessage());
            return;
        }
        logger.accept("Created " + (file ? "file" : "directory") + " at " + this.root.toAbsolutePath());
    }

    abstract void load();

    abstract void save();

}
