package dev.sweety.config.common;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public abstract class FileContainer {

    protected final File root;

    public FileContainer(File root) {
        this.root = root;
    }

    public FileContainer(File parent, String name, boolean file, Consumer<String> logger) {
        this.root = new File(parent, name);
        try {
            if (!this.root.exists() && file ? !this.root.createNewFile() : !this.root.mkdirs()) return;
        } catch (IOException e) {
            logger.accept("Failed to create file at " + this.root.getAbsolutePath() + ": " + e.getMessage());
            return;
        }
        logger.accept("Created " + (file ? "file" : "directory") + " at " + this.root.getAbsolutePath());
    }

    abstract void load();

    abstract void save();

}
