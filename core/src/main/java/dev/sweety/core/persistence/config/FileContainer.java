package dev.sweety.core.persistence.config;

import dev.sweety.core.util.Loadable;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;

@Getter
public abstract class FileContainer implements Loadable {

    protected final File rootDir;

    public FileContainer(File rootDir) {
        this.rootDir = rootDir;
    }

    @SneakyThrows
    public FileContainer(File parent, String name, boolean file) {
        this.rootDir = new File(parent, name);
        if (!this.rootDir.exists() && file ? this.rootDir.createNewFile() : this.rootDir.mkdirs()) {

        }
    }

}
