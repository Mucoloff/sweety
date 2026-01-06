package dev.sweety.module.extension;

import dev.sweety.module.loader.ExtensionClassLoader;
import dev.sweety.core.persistence.config.FileContainer;
import dev.sweety.event.EventSystem;
import dev.sweety.module.loader.DownloadFile;
import dev.sweety.core.logger.SimpleLogger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ExtensionManager extends FileContainer {

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

    private final Map<String, Extension> extensions = new HashMap<>();
    private final Map<Extension, ExtensionInfo> infos = new HashMap<>();
    private final SimpleLogger logger = new SimpleLogger(ExtensionManager.class).fallback();
    private final EventSystem eventSystem;

    public ExtensionManager(File parent, EventSystem eventSystem) {
        super(parent, Extension.NAME + "s", false);
        this.eventSystem = eventSystem;
    }

    /**
     * Carica un'estensione da un URL, la scarica nella directory delle estensioni e la abilita.
     *
     * @param url L'URL del file JAR dell'estensione.
     * @return Un CompletableFuture che conterrà l'estensione caricata, o un'eccezione se il caricamento fallisce.
     */
    public CompletableFuture<Extension> loadExtensionFromUrl(String url) {
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
    public Extension loadExtension(File jarFile) {
        try {
            final ExtensionInfo info = ExtensionInfo.get(jarFile);

            if (extensions.containsKey(info.name())) {
                logger.error("Impossibile caricare " + Extension.NAME + " " + jarFile.getName() + ": Un " + Extension.NAME + " con il nome '" + info.name() + "' esiste già.");
                return null;
            }

            final Extension extension;
            try (ExtensionClassLoader classLoader = new ExtensionClassLoader(jarFile, info, Extension.class.getClassLoader(), rootDir)) {
                extension = classLoader.getExtension();
            } catch (Exception e) {
                logger.error("Impossibile caricare " + Extension.NAME + " " + jarFile.getName() + ": Fallita l'inizializzazione della classe principale.", e);
                return null;
            }

            logger.info(extension.getName() + " v" + info.version() + " è ora abilitato.");
            extension.setEnabled(true);
            eventSystem.subscribe(extension);

            extensions.put(extension.getName(), extension);
            this.infos.put(extension, info);
            return extension;
        } catch (Throwable thrown) {
            logger.error("Impossibile abilitare " + Extension.NAME + " " + jarFile.getName() + "!", thrown);
            return null;
        }
    }

    @Override
    public void load() {
        final File[] jars = rootDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jars == null) return;

        for (final File jarFile : jars) {
            loadExtension(jarFile);
        }
    }

    @Override
    public void save() {
        extensions.values().forEach(extension -> {
            try {
                extension.setEnabled(false);
                eventSystem.unsubscribe(extension);
                final ClassLoader classLoader = extension.getClass().getClassLoader();

                if (classLoader instanceof ExtensionClassLoader) ((ExtensionClassLoader) classLoader).close();

                logger.info(extension.getName() + " v" + infos.get(extension).version() + " is now disabled.");
            } catch (Exception ex) {
                logger.error("Could not disable " + Extension.NAME + " " + extension.getName() + "!", ex);
            }
        });
        extensions.clear();
        infos.clear();
    }

    public Extension get(final String name) {
        return extensions.get(name);
    }

    public ExtensionInfo get(final Extension extension) {
        return infos.get(extension);
    }
}