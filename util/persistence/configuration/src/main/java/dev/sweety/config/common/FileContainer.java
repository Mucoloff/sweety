package dev.sweety.config.common;

import lombok.Getter;
import lombok.SneakyThrows;
import org.tomlj.Toml;

import java.io.File;
import java.util.function.Consumer;

@Getter
public abstract class FileContainer {

    protected final File root;

    public FileContainer(File root) {
        this.root = root;
    }

    @SneakyThrows
    public FileContainer(File parent, String name, boolean file, Consumer<String> logger) {
        this.root = new File(parent, name);
        if (!this.root.exists() && file ? !this.root.createNewFile() : !this.root.mkdirs()) return;
        logger.accept("Created " + (file ? "file" : "directory") + " at " + this.root.getAbsolutePath());
    }

    abstract void load();

    abstract void save();

}
