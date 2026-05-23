package dev.sweety.extension.manager;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.extension.manager.loader.DownloadFile;
import dev.sweety.extension.manager.loader.DownloadPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class DownloadFileTest {

    // ------------------------------------------------------------------
    // validateFileName
    // ------------------------------------------------------------------

    @Test
    void validateFileName_replacesInvalidChars() {
        // Each character that must be replaced: \ / : * ? " < > |
        String[] invalidChars = {"\\", "/", ":", "*", "?", "\"", "<", ">", "|"};

        for (String ch : invalidChars) {
            String input = "file" + ch + "name.jar";
            String result = DownloadFile.validateFileName(input);
            assertFalse(result.contains(ch),
                    "validateFileName should replace '" + ch + "' with '-', but result was: " + result);
            assertTrue(result.contains("-"),
                    "validateFileName should introduce a '-' for invalid char '" + ch + "'");
        }
    }

    @Test
    void validateFileName_returnsNullForNullInput() {
        assertNull(DownloadFile.validateFileName(null),
                "validateFileName(null) should return null");
    }

    @Test
    void validateFileName_leavesValidNameUntouched() {
        String valid = "my-plugin_v1.0.jar";
        assertEquals(valid, DownloadFile.validateFileName(valid),
                "validateFileName should not alter a name that contains no invalid characters");
    }

    // ------------------------------------------------------------------
    // downloadFromUrl_savesToDisk
    // ------------------------------------------------------------------

    @Test
    void downloadFromUrl_savesToDisk(@TempDir Path tmp) throws Exception {
        byte[] content = "hello from embedded server".getBytes();

        HttpServer server = startServer(content);
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/file.bin";
            Path target = tmp.resolve("downloaded.bin");

            CompletableFuture<Path> future = DownloadFile.downloadFromURL(url, target, true, DownloadPolicy.ALLOW_HTTP);
            Path result = future.get();

            assertEquals(target, result, "Returned path should be the target path when saveToDisk=true");
            assertTrue(Files.exists(target), "Target file must exist on disk");
            assertArrayEquals(content, Files.readAllBytes(target),
                    "Downloaded file content must match what the server served");
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // downloadFromUrl_tempFile
    // ------------------------------------------------------------------

    @Test
    void downloadFromUrl_tempFile(@TempDir Path tmp) throws Exception {
        byte[] content = "temp file content".getBytes();

        HttpServer server = startServer(content);
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/data.bin";
            // targetPath is only used to derive the temp file suffix; it need not exist
            Path nominal = tmp.resolve("data.bin");

            CompletableFuture<Path> future = DownloadFile.downloadFromURL(url, nominal, false, DownloadPolicy.ALLOW_HTTP);
            Path result = future.get();

            assertNotNull(result, "Future must complete with a non-null path");
            assertNotEquals(nominal, result,
                    "Returned path should be a temp file, not the nominal target path");
            assertTrue(Files.exists(result), "Temp file must exist on disk");
            assertArrayEquals(content, Files.readAllBytes(result),
                    "Temp file content must match what the server served");

            // Clean up temp file to avoid leftover artefacts
            Files.deleteIfExists(result);
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // downloadFromUrl_exceededMaxBytes_throws
    // ------------------------------------------------------------------

    @Test
    void downloadFromUrl_exceededMaxBytes_throws(@TempDir Path tmp) throws Exception {
        byte[] content = "0123456789".getBytes(); // 10 bytes

        HttpServer server = startServer(content);
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/big.bin";
            Path target = tmp.resolve("big.bin");

            DownloadPolicy tinyPolicy = new DownloadPolicy(
                    java.time.Duration.ofSeconds(10),
                    java.time.Duration.ofSeconds(30),
                    3L, // max 3 bytes — server sends 10
                    Set.of("http"),
                    Optional.empty()
            );

            CompletableFuture<Path> future = DownloadFile.downloadFromURL(url, target, true, tinyPolicy);

            ExecutionException ex = assertThrows(ExecutionException.class, future::get,
                    "Future must complete exceptionally when payload exceeds maxBytes");
            Throwable cause = ex.getCause();
            assertNotNull(cause, "Cause must not be null");
            assertInstanceOf(RuntimeException.class, cause);

            // Walk the cause chain to find the IOException with our message
            boolean found = false;
            Throwable t = cause;
            while (t != null) {
                if (t instanceof java.io.IOException && t.getMessage() != null
                        && t.getMessage().contains("payload exceeds maxBytes")) {
                    found = true;
                    break;
                }
                t = t.getCause();
            }
            assertTrue(found, "Cause chain must contain an IOException with 'payload exceeds maxBytes'");
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // downloadFromUrl_disallowedScheme_throws
    // ------------------------------------------------------------------

    @Test
    void downloadFromUrl_disallowedScheme_throws(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.bin");

        // Policy that only allows https — we pass a file:// URL
        DownloadPolicy httpsOnly = new DownloadPolicy(
                java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(30),
                50 * 1024 * 1024L,
                Set.of("https"),
                Optional.empty()
        );

        // file:// URL pointing to something that exists (doesn't matter — scheme check is first)
        String fileUrl = tmp.toUri().toString(); // e.g. file:///tmp/...

        CompletableFuture<Path> future = DownloadFile.downloadFromURL(fileUrl, target, true, httpsOnly);

        ExecutionException ex = assertThrows(ExecutionException.class, future::get,
                "Future must complete exceptionally for disallowed scheme");
        Throwable cause = ex.getCause();
        assertNotNull(cause, "Cause must not be null");
        assertInstanceOf(RuntimeException.class, cause);

        boolean found = false;
        Throwable t = cause;
        while (t != null) {
            if (t instanceof java.io.IOException && t.getMessage() != null
                    && t.getMessage().contains("Scheme not allowed")) {
                found = true;
                break;
            }
            t = t.getCause();
        }
        assertTrue(found, "Cause chain must contain an IOException with 'Scheme not allowed'");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Starts an embedded {@link HttpServer} on a random port that responds to every GET
     * request with {@code body}. Caller is responsible for calling {@code server.stop(0)}.
     */
    private static HttpServer startServer(byte[] body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return server;
    }
}
