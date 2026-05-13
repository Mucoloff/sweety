package dev.sweety.extension.manager;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.manager.loader.DownloadFile;
import dev.sweety.extension.manager.loader.ExtensionClassLoader;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;

public class ExtensionManager<T extends Extension> {

    protected final Path rootDir;
    private final Map<String, T> extensions = new ConcurrentHashMap<>();
    private final Map<T, ExtensionInfo> infos = new ConcurrentHashMap<>();
    /** Kept open until {@link #unloadExtension(String)} so lazy class/resource loading keeps working. */
    private final Map<T, ExtensionClassLoader<T>> classLoaders = new ConcurrentHashMap<>();
    private final SimpleLogger logger = new SimpleLogger(ExtensionManager.class);
    private final Class<T> extensionClass;
    private final String extensionName;

    public ExtensionManager(final Path parent, final Class<T> extensionClass) {
        this.extensionClass = extensionClass;
        this.extensionName = extensionClass.getSimpleName().toLowerCase();
        this.rootDir = parent.resolve(extensionName + "s");
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

        logger.info("Avvio download dell'estensione da " + url);

        return DownloadFile.downloadFromURL(url, localFile, true)
                .thenApply(this::loadExtension);
    }

    public T loadExtension(final Path jarFile) {
        try {
            final ExtensionInfo info = ExtensionInfo.of(jarFile, this.extensionName.toLowerCase(Locale.ROOT));

            if (extensions.containsKey(info.name())) {
                logger.error("Impossibile caricare " + this.extensionName + " " + jarFile.getFileName() + ": Un " + this.extensionName + " con il nome '" + info.name() + "' esiste già.");
                return null;
            }

            ExtensionClassLoader<T> classLoader = null;
            final T extension;
            try {
                classLoader = new ExtensionClassLoader<>(jarFile, info, this.extensionClass, this.rootDir);
                extension = classLoader.extension();
            } catch (Exception e) {
                if (classLoader != null) {
                    try {
                        classLoader.close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                logger.error("Impossibile caricare " + this.extensionName + " " + jarFile.getFileName() + ": Fallita l'inizializzazione della classe principale.", e);
                return null;
            }

            if (this.extensions.putIfAbsent(extension.name(), extension) != null) {
                try { classLoader.close(); } catch (Exception ignored) {}
                logger.error("Conflitto di caricamento concorrente: Un " + this.extensionName + " con il nome '" + info.name() + "' è stato appena caricato.");
                return null;
            }

            this.logger.info(extension.name() + " v" + info.version() + " è ora abilitato.");
            extension.setEnabled(true);

            this.infos.put(extension, info);
            this.classLoaders.put(extension, classLoader);
            return extension;
        } catch (Throwable thrown) {
            logger.error("Impossibile abilitare " + this.extensionName + " " + jarFile.getFileName() + "!", thrown);
            return null;
        }
    }

    public void load() {
        if (!Files.isDirectory(rootDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir, "*.jar")) {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                for (Path jarFile : stream) {
                    scope.fork(() -> loadExtension(jarFile));
                }
                scope.join();
                scope.throwIfFailed();
            }
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
        return this.extensions;
    }

    public Map<T, ExtensionInfo> infos() {
        return this.infos;
    }
}
