package dev.sweety.module.loader;

import lombok.experimental.UtilityClass;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@UtilityClass
public class DownloadFile {

    /**
     * Scarica un JAR in memoria e lo carica dinamicamente nel classpath.
     * Non lascia tracce su disco.
     */
    public CompletableFuture<ClassLoader> loadJarInMemory(String jarUrl, ClassLoader parent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] jarBytes = downloadBytes(jarUrl);
                return injectJarBytes(jarBytes, parent);
            } catch (Exception e) {
                throw new RuntimeException("Errore durante il caricamento del JAR", e);
            }
        });
    }

    /**
     * Scarica un file remoto in memoria come array di byte.
     */
    public byte[] downloadBytes(String urlStr) throws Exception {
        URL url = new URI(urlStr).toURL();
        try (InputStream in = url.openStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            in.transferTo(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Crea un ClassLoader temporaneo con il JAR in memoria.
     * Non scrive nulla su disco.
     */
    private ClassLoader injectJarBytes(byte[] jarBytes, ClassLoader parent) throws IOException {
        // Convertiamo i bytes in una URL "virtuale" (in-memory)
        // usando un custom URLStreamHandler
        var handler = new InMemoryJarURLHandler(jarBytes);
        URL jarUrl = new URL(null, "memoryjar://temp", handler);

        // Creiamo un classloader che lo carichi da quella URL
        return new URLClassLoader(new URL[]{jarUrl}, parent);
    }

    public CompletableFuture<File> downloadFromURL(String urlStr, String fileName, boolean saveToDisk) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final URL url = new URI(urlStr).toURL();
                //final Path filePath
                final File file = saveToDisk
                        ? Path.of(fileName).toFile()
                        : new File("download_" + validate(fileName));
                //: Files.createTempFile("download_", "_" + validate(fileName));
                //final File file = filePath.toFile();

                if (saveToDisk) {
                    if (file.getParentFile() == null || !file.getParentFile().mkdirs()) {
                        file.createNewFile();
                    }
                } else {
                    file.deleteOnExit();
                }

                try (InputStream in = url.openStream()) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(in.readAllBytes());
                    }
                    //Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
                }

                return file;
            } catch (Exception e) {
                throw new RuntimeException("Errore durante il download", e);
            }
        });
    }

    private String validate(String fileName) {
        if (fileName == null) return null;

        String invalidChars = "[\\\\/:*?\"<>|]";
        return fileName.replaceAll(invalidChars, "-");
    }
}

