package dev.sweety.extension.versioning;

import dev.sweety.extension.Extension;
import dev.sweety.extension.manager.ExtensionManager;
import dev.sweety.util.logger.SimpleLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateableExtensionManager<T extends Extension> extends ExtensionManager<T> {

    private final Map<T, File> extensionFiles = new ConcurrentHashMap<>();
    private final SimpleLogger logger = new SimpleLogger(UpdateableExtensionManager.class);

    public UpdateableExtensionManager(File parent, Class<T> extensionClass) {
        super(parent, extensionClass);
    }

    @Override
    public T loadExtension(File jarFile) {
        // Handle .update files before loading
        File updateFile = new File(jarFile.getParent(), jarFile.getName() + ".update");
        if (updateFile.exists()) {
            try {
                Files.move(updateFile.toPath(), jarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                logger.info("Applied update for " + jarFile.getName());
            } catch (IOException e) {
                logger.error("Failed to apply update for " + jarFile.getName(), e);
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

    public File getFile(T extension) {
        return extensionFiles.get(extension);
    }
}
