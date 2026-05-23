package dev.sweety.extension.manager;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.manager.loader.DownloadFile;
import dev.sweety.extension.manager.loader.DownloadPolicy;
import dev.sweety.extension.manager.loader.ExtensionClassLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ExtensionManager<T extends Extension> {

    protected final Path rootDir;
    private final Map<String, T> extensions = new ConcurrentHashMap<>();
    private final Map<T, ExtensionInfo> infos = new ConcurrentHashMap<>();
    /** Kept open until {@link #unloadExtension(String)} so lazy class/resource loading keeps working. */
    private final Map<T, ExtensionClassLoader<T>> classLoaders = new ConcurrentHashMap<>();
    private final SimpleLogger logger;
    private final Class<T> extensionClass;
    private final String extensionName;

    public ExtensionManager(final Path parent, final Class<T> extensionClass) {
        this(parent, extensionClass, new SimpleLogger(ExtensionManager.class));
    }

    public ExtensionManager(final Path parent, final Class<T> extensionClass, final SimpleLogger logger) {
        this.extensionClass = extensionClass;
        this.extensionName = extensionClass.getSimpleName().toLowerCase();
        this.rootDir = parent.resolve(extensionName + "s");
        this.logger = logger;
        try {
            if (!Files.isDirectory(rootDir)) Files.createDirectories(rootDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path getRootDir() {
        return rootDir;
    }

    public CompletableFuture<T> loadExtensionFromUrl(final String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path localFile = rootDir.resolve(fileName);

        logger.info("Starting extension download from " + url);

        return DownloadFile.downloadFromURL(url, localFile, true, DownloadPolicy.DEFAULT)
                .exceptionally(ex -> {
                    logger.error("Failed to download extension from " + url, ex);
                    return null;
                })
                .thenApply(path -> path == null ? null : loadExtension(path));
    }

    public T loadExtension(final Path jarFile) {
        try {
            final ExtensionInfo info = ExtensionInfo.of(jarFile, this.extensionName.toLowerCase(Locale.ROOT));

            if (extensions.containsKey(info.name())) {
                logger.error("Cannot load " + this.extensionName + " " + jarFile.getFileName() + ": A " + this.extensionName + " with name '" + info.name() + "' already exists.");
                return null;
            }

            ExtensionClassLoader<T> classLoader = null;
            final T extension;
            try {
                classLoader = new ExtensionClassLoader<>(jarFile, info, this.extensionClass, this.rootDir);
                extension = classLoader.extension();
            } catch (Throwable e) {
                if (classLoader != null) {
                    try {
                        classLoader.close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                logger.error("Cannot load " + this.extensionName + " " + jarFile.getFileName() + ": Failed to initialize main class.", e);
                return null;
            }

            if (this.extensions.putIfAbsent(extension.name(), extension) != null) {
                try { classLoader.close(); } catch (Exception ignored) {}
                logger.error("Concurrent load conflict: A " + this.extensionName + " with name '" + info.name() + "' was just loaded.");
                return null;
            }

            this.logger.info(extension.name() + " v" + info.version() + " is now enabled.");
            extension.setEnabled(true);

            this.infos.put(extension, info);
            this.classLoaders.put(extension, classLoader);
            return extension;
        } catch (Throwable thrown) {
            logger.error("Cannot enable " + this.extensionName + " " + jarFile.getFileName() + "!", thrown);
            return null;
        }
    }

    public void load() {
        if (!Files.isDirectory(rootDir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir, "*.jar")) {
            List<Path> jars = new ArrayList<>();
            stream.forEach(jars::add);
            // Load each jar in parallel, isolate failures
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Path jarFile : jars) {
                futures.add(CompletableFuture.runAsync(() -> {
                    T ext = loadExtension(jarFile);
                    if (ext == null) {
                        logger.warn("Skipping " + jarFile.getFileName() + ": load returned null");
                    }
                }));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            logger.error("Failed to load extensions in parallel", e);
        }
    }

    public T unloadExtension(final String name) {
        final T extension = this.extensions.remove(name);
        if (extension == null) {
            this.logger.warn("Could not disable " + this.extensionName + " '" + name + "': Not found.");
            return null;
        }

        try {
            extension.setEnabled(false);
            ExtensionClassLoader<T> owned = this.classLoaders.remove(extension);
            if (owned != null) {
                owned.close();
            } else {
                ClassLoader cl = extension.getClass().getClassLoader();
                if (cl instanceof ExtensionClassLoader<?> extensionLoader) extensionLoader.close();
            }

            this.logger.info(extension.name() + " v" + this.infos.get(extension).version() + " is now disabled.");
        } catch (Exception ex) {
            this.logger.error("Could not disable " + this.extensionName + " " + extension.name() + "!", ex);
        } finally {
            this.infos.remove(extension);
        }
        return extension;
    }

    public void shutdown() {
        new ArrayList<>(this.extensions.keySet()).forEach(this::unloadExtension);
        this.extensions.clear();
        this.infos.clear();
        for (ExtensionClassLoader<T> cl : new ArrayList<>(this.classLoaders.values())) {
            try {
                cl.close();
            } catch (Exception ignored) {}
        }
        this.classLoaders.clear();
    }

    public T get(final String name) {
        return this.extensions.get(name);
    }

    public ExtensionInfo get(final T extension) {
        return this.infos.get(extension);
    }

    public Map<String, T> extensions() {
        return Collections.unmodifiableMap(this.extensions);
    }

    public Map<T, ExtensionInfo> infos() {
        return Collections.unmodifiableMap(this.infos);
    }
}
