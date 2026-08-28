package dev.sweety.extension.manager;

import dev.sweety.i18n.Messages;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.manager.loader.DownloadFile;
import dev.sweety.extension.manager.loader.DownloadPolicy;
import dev.sweety.extension.manager.loader.ExtensionClassLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

    // Namespaced bundle name (extension/…) — a bare "messages" collides at classpath root with
    // extension-versioning:manager's own messages_*.yml; whichever loads first wins, so keys from
    // the other bundle resolve to their literal (e.g. "extension.load.duplicate"). Explicit loader
    // REQUIRED too: at runtime the yml ship in this module's jar, invisible to Messages.class's loader.
    private static final Messages MESSAGES = Messages.forBundle("extension/messages", ExtensionManager.class.getClassLoader());

    protected final Path rootDir;
    private final Map<String, T> extensions = new ConcurrentHashMap<>();
    private final Map<T, ExtensionInfo> infos = new ConcurrentHashMap<>();
    /** Kept open until {@link #unloadExtension(String)} so lazy class/resource loading keeps working. */
    private final Map<T, ExtensionClassLoader<T>> classLoaders = new ConcurrentHashMap<>();
    private final SimpleLogger logger;
    private final Class<T> extensionClass;
    private final String extensionName;
    /**
     * Parent classloader for {@link ExtensionClassLoader} (isolated-loader path). Defaults to the
     * classloader that loaded {@code extensionClass} — pass the game/MC classloader so extension
     * code can resolve Minecraft + host types. NOTE: this does NOT make dynamic mixins apply; the
     * child loader defines the extension classes itself, out of the transforming loader's reach.
     * For mixin-bearing addons use {@link #mount(Path, ClassLoader)} (defines on the game loader).
     */
    private final ClassLoader parentLoader;

    public ExtensionManager(final Path parent, final Class<T> extensionClass) {
        this(parent, extensionClass, SimpleLogger.of(ExtensionManager.class));
    }

    public ExtensionManager(final Path parent, final Class<T> extensionClass, final SimpleLogger logger) {
        this(parent, extensionClass, logger, extensionClass.getClassLoader());
    }

    public ExtensionManager(final Path parent, final Class<T> extensionClass, final SimpleLogger logger,
                            final ClassLoader parentLoader) {
        this.extensionClass = extensionClass;
        this.extensionName = extensionClass.getSimpleName().toLowerCase(Locale.ROOT);
        this.rootDir = parent.resolve(extensionName + "s");
        this.logger = logger;
        this.parentLoader = parentLoader;
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

        logger.info(MESSAGES.get("extension.download.start", url));

        return DownloadFile.downloadFromURL(url, localFile, true, DownloadPolicy.DEFAULT)
                .exceptionally(ex -> {
                    logger.error(MESSAGES.get("extension.download.failed", url), ex);
                    return null;
                })
                .thenApply(path -> path == null ? null : loadExtension(path));
    }

    public T loadExtension(final Path jarFile) {
        try {
            final ExtensionInfo info = ExtensionInfo.of(jarFile, this.extensionName);

            if (extensions.containsKey(info.name())) {
                logger.error(MESSAGES.get("extension.load.duplicate",
                        this.extensionName, jarFile.getFileName(), info.name()));
                return null;
            }

            final List<String> missing = info.missingRequirements(this.parentLoader);
            if (!missing.isEmpty()) {
                logger.warn(MESSAGES.get("extension.load.missingRequires", this.extensionName, info.name(), missing));
                return null;
            }

            ExtensionClassLoader<T> classLoader = null;
            final T extension;
            try {
                classLoader = new ExtensionClassLoader<>(jarFile, info, this.extensionClass, this.rootDir, this.parentLoader);
                extension = classLoader.extension();
            } catch (Throwable e) {
                if (classLoader != null) {
                    try {
                        classLoader.close();
                    } catch (Exception closeEx) {
                        e.addSuppressed(closeEx);
                    }
                }
                logger.error(MESSAGES.get("extension.load.initFailed",
                        this.extensionName, jarFile.getFileName()), e);
                return null;
            }

            if (this.extensions.putIfAbsent(extension.name(), extension) != null) {
                try { classLoader.close(); } catch (Exception e) { logger.warn("Failed to close duplicate classloader", e); }
                logger.error(MESSAGES.get("extension.load.concurrentConflict",
                        this.extensionName, info.name()));
                return null;
            }

            this.logger.info(MESSAGES.get("extension.enabled", extension.name(), info.version()));
            extension.setEnabled(true);

            this.infos.put(extension, info);
            this.classLoaders.put(extension, classLoader);
            return extension;
        } catch (Throwable thrown) {
            logger.error(MESSAGES.get("extension.enable.failed",
                    this.extensionName, jarFile.getFileName()), thrown);
            return null;
        }
    }

    public void load() {
        if (!Files.isDirectory(rootDir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir, "*.jar")) {
            List<Path> jars = new ArrayList<>();
            stream.forEach(jars::add);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Path jarFile : jars) {
                futures.add(CompletableFuture.runAsync(() -> {
                    T ext = loadExtension(jarFile);
                    if (ext == null) {
                        logger.warn(MESSAGES.get("extension.load.skip", jarFile.getFileName()));
                    }
                }));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            logger.error(MESSAGES.get("extension.load.parallelFailed"), e);
        }
    }

    public T unloadExtension(final String name) {
        final T extension = this.extensions.remove(name);
        if (extension == null) {
            this.logger.warn(MESSAGES.get("extension.disable.notFound", this.extensionName, name));
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

            this.logger.info(MESSAGES.get("extension.disabled",
                    extension.name(), this.infos.get(extension).version()));
        } catch (Exception ex) {
            this.logger.error(MESSAGES.get("extension.disable.failed",
                    this.extensionName, extension.name()), ex);
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
            } catch (Exception e) {
                logger.warn("Failed to close class loader during shutdown", e);
            }
        }
        this.classLoaders.clear();
    }

    /**
     * Registers an extension that was instantiated <em>externally</em> (e.g. mounted on the game
     * classloader for mixin support, instead of the framework's isolated {@link ExtensionClassLoader}).
     * No classloader is owned by the manager — the caller controls the JAR's lifetime. The extension
     * is stored disabled; the caller enables it. Returns {@code null} on a duplicate name.
     */
    public T register(final T extension, final ExtensionInfo info) {
        if (this.extensions.putIfAbsent(extension.name(), extension) != null) {
            logger.error(MESSAGES.get("extension.load.duplicate", this.extensionName, info.name(), extension.name()));
            return null;
        }
        this.infos.put(extension, info);
        return extension;
    }

    /**
     * Mixin-capable load: instantiates the extension on the given <em>game</em> classloader instead of
     * the framework's isolated {@link ExtensionClassLoader}. The JAR must ALREADY be mounted on that
     * classloader (Fabric {@code addToClassPath} / NeoForge {@code SecureJar}) so its mixins transform
     * game classes — a child URLClassLoader can never achieve that, since it would define the extension
     * classes itself, out of the transforming loader's reach.
     *
     * <p>Reads {@code <extensionName>.json}, builds via the standard
     * {@code (name, version, description, dataFolder, logger)} constructor, and {@link #register}s it
     * <em>disabled</em>. Mixin-config registration + {@code enable()} are the caller's job. Returns
     * {@code null} on a duplicate name.
     *
     * @param jar        the (already game-classloader-mounted) extension JAR
     * @param gameLoader the game classloader the JAR was mounted on
     */
    public T mount(final Path jar, final ClassLoader gameLoader) throws Exception {
        return mount(gameLoader, ExtensionInfo.of(jar, this.extensionName));
    }

    /**
     * Mixin-capable load for in-memory addon classloaders: reads {@code <extensionName>.json} from
     * {@code addonLoader}'s resources (no JAR file on disk), then instantiates the main class from
     * {@code addonLoader} directly (the addon was already indexed into it by {@code MemoryJarSource}).
     *
     * <p>Use this overload on Fabric when the addon was delivered encrypted and mounted in-memory.
     * Use {@link #mount(Path, ClassLoader)} on NeoForge or for the dev on-disk path.
     *
     * @param addonLoader the {@code MemoryJarClassLoader} holding the addon's classes and resources
     * @param gameLoader  the game classloader (Knot); used only as the delegation parent — the addon
     *                    classes themselves are defined on {@code addonLoader}
     */
    public T mount(final ClassLoader addonLoader, final ClassLoader gameLoader) throws Exception {
        return mount(gameLoader, ExtensionInfo.of(addonLoader, this.extensionName));
    }

    private T mount(ClassLoader gameLoader, ExtensionInfo info) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // Checked before the main class is even resolved: an extension that wraps a third-party mod is
        // not merely degraded without it, it is a crash waiting for the first tick that reaches its code.
        final List<String> missing = info.missingRequirements(gameLoader);
        if (!missing.isEmpty()) {
            logger.warn(MESSAGES.get("extension.load.missingRequires", this.extensionName, info.name(), missing));
            return null;
        }

        final Class<?> mainClass = Class.forName(info.main(), true, gameLoader);
        if (!this.extensionClass.isAssignableFrom(mainClass)) {
            throw new IllegalStateException(info.main() + " does not extend " + this.extensionClass.getSimpleName());
        }

        final Constructor<? extends T> ctor = mainClass.asSubclass(this.extensionClass)
                .getDeclaredConstructor(String.class, String.class, String.class, Path.class, SimpleLogger.class);
        ctor.setAccessible(true);
        final T extension = ctor.newInstance(
                info.name(), info.version(), info.description(), this.rootDir, SimpleLogger.of(mainClass));

        return register(extension, info);
    }

    public T get(final String name) {
        return this.extensions.get(name);
    }

    public boolean isLoaded(final String name) {
        return this.extensions.containsKey(name);
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
