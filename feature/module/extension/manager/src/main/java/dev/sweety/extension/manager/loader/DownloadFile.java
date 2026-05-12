package dev.sweety.extension.manager.loader;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

public final class DownloadFile {

    public static CompletableFuture<Path> downloadFromURL(String urlStr, Path targetPath, boolean saveToDisk) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final URL url = new URI(urlStr).toURL();
                final Path file = saveToDisk
                        ? targetPath
                        : Files.createTempFile("download_", "_" + validateFileName(targetPath.getFileName().toString()));

                if (saveToDisk) {
                    Path parent = file.getParent();
                    if (parent != null) Files.createDirectories(parent);
                }

                try (InputStream in = url.openStream()) {
                    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                }

                return file;
            } catch (Exception e) {
                throw new RuntimeException("Errore durante il download", e);
            }
        });
    }

    public static String validateFileName(String fileName) {
        if (fileName == null) return null;

        String invalidChars = "[\\\\/:*?\"<>|]";
        return fileName.replaceAll(invalidChars, "-");
    }
}
