package dev.sweety.extension.manager.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public final class DownloadFile {

    public static CompletableFuture<Path> downloadFromURL(String urlStr, Path targetPath, boolean saveToDisk) {
        return downloadFromURL(urlStr, targetPath, saveToDisk, DownloadPolicy.DEFAULT);
    }

    public static CompletableFuture<Path> downloadFromURL(String urlStr, Path targetPath, boolean saveToDisk, DownloadPolicy policy) {
        return CompletableFuture.supplyAsync(() -> {
            Path file = null;
            try {
                URI uri = URI.create(urlStr);
                String scheme = uri.getScheme();
                if (scheme == null || !policy.allowedSchemes().contains(scheme.toLowerCase())) {
                    throw new IOException("Scheme not allowed: " + scheme);
                }

                file = saveToDisk
                        ? targetPath
                        : Files.createTempFile("download_", "_" + validateFileName(targetPath.getFileName().toString()));

                if (saveToDisk) {
                    Path parent = file.getParent();
                    if (parent != null) Files.createDirectories(parent);
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(policy.connectTimeout())
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(policy.readTimeout())
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                MessageDigest digest = policy.expectedSha256Hex().isPresent()
                        ? MessageDigest.getInstance("SHA-256")
                        : null;

                try (InputStream in = response.body()) {
                    long totalRead = 0L;
                    byte[] buffer = new byte[8192];
                    int read;
                    // Write directly to a temp staging path then move, to avoid partial writes
                    Path stagingPath = Files.createTempFile(
                            file.getParent() != null ? file.getParent() : Path.of(System.getProperty("java.io.tmpdir")),
                            ".staging_", null);
                    try {
                        try (var out = Files.newOutputStream(stagingPath)) {
                            while ((read = in.read(buffer)) != -1) {
                                totalRead += read;
                                if (totalRead > policy.maxBytes()) {
                                    throw new IOException("payload exceeds maxBytes: " + policy.maxBytes());
                                }
                                out.write(buffer, 0, read);
                                if (digest != null) {
                                    digest.update(buffer, 0, read);
                                }
                            }
                        }

                        if (digest != null) {
                            String actual = HexFormat.of().formatHex(digest.digest());
                            String expected = policy.expectedSha256Hex().get();
                            if (!expected.equalsIgnoreCase(actual)) {
                                throw new IOException("Checksum mismatch");
                            }
                        }

                        Files.move(stagingPath, file, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        Files.deleteIfExists(stagingPath);
                        throw ex;
                    }
                }

                return file;
            } catch (IOException e) {
                if (file != null && !saveToDisk) {
                    try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                }
                throw new RuntimeException("Download failed", e);
            } catch (Exception e) {
                if (file != null && !saveToDisk) {
                    try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                }
                throw new RuntimeException("Download failed", e);
            }
        });
    }

    public static String validateFileName(String fileName) {
        if (fileName == null) return null;

        String invalidChars = "[\\\\/:*?\"<>|]";
        return fileName.replaceAll(invalidChars, "-");
    }
}
