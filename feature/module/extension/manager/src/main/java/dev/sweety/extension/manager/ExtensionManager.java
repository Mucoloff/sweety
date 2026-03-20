package dev.sweety.extension.manager;

import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.extension.Extension;
import dev.sweety.extension.ExtensionInfo;
import dev.sweety.extension.manager.loader.DownloadFile;
import dev.sweety.extension.manager.loader.ExtensionClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ExtensionManager<T extends Extension> {

    /*
    private static Method INIT;

    static {
        try {
            INIT = Extension.class.getDeclaredMethod("init", String.class, File.class, SimpleLogger.class);
            INIT.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }
    }
     */

    protected final File rootDir;
    private final Map<String, T> extensions = new HashMap<>();
    private final Map<T, ExtensionInfo> infos = new HashMap<>();
    private final SimpleLogger logger = new SimpleLogger(ExtensionManager.class);
    private final Class<T> extensionClass;
    private final String extensionName;

    public ExtensionManager(final File parent, final Class<T> extensionClass) {
        this.extensionClass = extensionClass;
        this.extensionName = extensionClass.getSimpleName().toLowerCase();
        this.rootDir = new File(parent, extensionName + "s");
        if (!this.rootDir.exists()) this.rootDir.mkdirs();
    }

    /**
     * Carica un'estensione da un URL, la scarica nella directory delle estensioni e la abilita.
     *
     * @param url L'URL del file JAR dell'estensione.
     * @return Un CompletableFuture che conterrà l'estensione caricata, o un'eccezione se il caricamento fallisce.
     */
    public CompletableFuture<T> loadExtensionFromUrl(final String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        File localFile = new File(rootDir, fileName);

        logger.info("Avvio download dell'estensione da " + url);

        return DownloadFile.downloadFromURL(url, localFile.getAbsolutePath(), true)
                .thenApply(this::loadExtension);
    }

    /**
     * Carica una singola estensione da un file JAR.
     *
     * @param jarFile Il file JAR dell'estensione.
     * @return L'istanza dell'estensione caricata, o null se il caricamento fallisce.
     */
    public T loadExtension(final File jarFile) {
        try {
            final ExtensionInfo info = ExtensionInfo.of(jarFile, this.extensionName.toLowerCase(Locale.ROOT));

            if (extensions.containsKey(info.name())) {
                logger.error("Impossibile caricare " + this.extensionName + " " + jarFile.getName() + ": Un " + this.extensionName + " con il nome '" + info.name() + "' esiste già.");
                return null;
            }

            final T extension;
            try (ExtensionClassLoader<T> classLoader = new ExtensionClassLoader<>(jarFile, info, this.extensionClass, this.rootDir)) {
                extension = classLoader.extension();
            } catch (Exception e) {
                logger.error("Impossibile caricare " + this.extensionName + " " + jarFile.getName() + ": Fallita l'inizializzazione della classe principale.", e);
                return null;
            }

            this.logger.info(extension.name() + " v" + info.version() + " è ora abilitato.");
            extension.setEnabled(true);

            this.extensions.put(extension.name(), extension);
            this.infos.put(extension, info);
            return extension;
        } catch (Throwable thrown) {
            logger.error("Impossibile abilitare " + this.extensionName + " " + jarFile.getName() + "!", thrown);
            return null;
        }
    }

    public void load() {
        final File[] jars = this.rootDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jars == null) return;

        for (final File jarFile : jars) {
            loadExtension(jarFile);
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
            final ClassLoader classLoader = extension.getClass().getClassLoader();

            if (classLoader instanceof ExtensionClassLoader<?> extensionLoader) extensionLoader.close();

            this.logger.info(extension.name() + " v" + this.infos.get(extension).version() + " is now disabled.");
        } catch (Exception ex) {
            this.logger.error("Could not disable " + this.extensionName + " " + extension.name() + "!", ex);
        } finally {
            this. infos.remove(extension);
        }
        return extension;
    }

    public void shutdown() {
        new ArrayList<>(this.extensions.keySet()).forEach(this::unloadExtension);
        this.extensions.clear();
        this.infos.clear();
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