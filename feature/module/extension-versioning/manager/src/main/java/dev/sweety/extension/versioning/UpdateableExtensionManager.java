package dev.sweety.extension.versioning;

import dev.sweety.extension.Extension;
import dev.sweety.extension.manager.ExtensionManager;
import dev.sweety.util.logger.SimpleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateableExtensionManager<T extends Extension> extends ExtensionManager<T> {

    static final String UPDATE_SUFFIX = ".update";

    private final Map<T, Path> extensionFiles = new ConcurrentHashMap<>();
    private final SimpleLogger logger = new SimpleLogger(UpdateableExtensionManager.class);

    public UpdateableExtensionManager(Path parent, Class<T> extensionClass) {
        super(Objects.requireNonNull(parent, "parent cannot be null"), Objects.requireNonNull(extensionClass, "extensionClass cannot be null"));
    }

    @SuppressWarnings("unchecked")
    public UpdateableExtensionManager(Path parent) {
        this(parent, (Class<T>) Extension.class);
    }

    public Path resolveFile(Path jarFile) {
        Objects.requireNonNull(jarFile, "jarFile cannot be null");
        Path updateFile = jarFile.resolveSibling(jarFile.getFileName().toString() + UPDATE_SUFFIX);
        return Files.exists(updateFile) ? updateFile : jarFile;
    }

    @Override
    public T loadExtension(Path jarFile) {
        Objects.requireNonNull(jarFile, "jarFile cannot be null");
        Path resolved = resolveFile(jarFile);

        if (!resolved.equals(jarFile) && Files.exists(resolved)) {
            try {
                Files.move(resolved, jarFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                logger.info("Applied update for " + jarFile.getFileName());
            } catch (IOException e) {
                logger.error("Failed to apply update for " + jarFile.getFileName(), e);
            }
        }

        T extension = super.loadExtension(jarFile);
        if (extension != null) {
            extensionFiles.put(extension, jarFile);
        }
        return extension;
    }

    @Override
    public T unloadExtension(String name) {
        T extension = super.unloadExtension(name);
        if (extension != null) {
            extensionFiles.remove(extension);
        }
        return extension;
    }

    public Path jarPath(T extension) {
        return extensionFiles.get(extension);
    }
}
