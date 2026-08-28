package dev.sweety.config.common;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public abstract class FileContainer {

    protected Path root;

    /** No-arg constructor for lazy-init subclasses that set {@code root} later via {@link #ensureExists}. */
    protected FileContainer() {}

    public FileContainer(@NotNull Path root) {
        this.root = root;
    }

    public FileContainer(Path parent, String name, boolean file, Consumer<String> logger) {
        this.root = parent.resolve(name);
        ensureExists(file, logger);
    }

    /**
     * Creates {@link #root} on disk if it does not yet exist.
     * Logs success/failure through {@code logger}; swallows {@link IOException}
     * (failure is reported to the logger, not rethrown).
     *
     * @param file {@code true} to create a file (with parent dirs), {@code false} for a directory.
     */
    protected void ensureExists(boolean file, Consumer<String> logger) {
        if (root == null) return;
        if (Files.exists(root)) return;
        try {
            if (file) {
                Path parent = root.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.createFile(root);
            } else {
                Files.createDirectories(root);
            }
            logger.accept("Created " + (file ? "file" : "directory") + " at " + root.toAbsolutePath());
        } catch (IOException e) {
            logger.accept("Failed to create " + root.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    public abstract void load();

    public abstract void save();

}
