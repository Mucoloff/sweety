package dev.sweety.extension.manager.loader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DownloadFile {

    public static CompletableFuture<File> downloadFromURL(String urlStr, String fileName, boolean saveToDisk) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final URL url = new URI(urlStr).toURL();
                //final Path filePath
                final File file = saveToDisk
                        ? Path.of(fileName).toFile()
                        : new File("download_" + validateFileName(fileName));
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

    public static String validateFileName(String fileName) {
        if (fileName == null) return null;

        String invalidChars = "[\\\\/:*?\"<>|]";
        return fileName.replaceAll(invalidChars, "-");
    }
}

