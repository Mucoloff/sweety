package dev.sweety.extension.manager;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.extension.manager.loader.DownloadFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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

            CompletableFuture<Path> future = DownloadFile.downloadFromURL(url, target, true);
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

            CompletableFuture<Path> future = DownloadFile.downloadFromURL(url, nominal, false);
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
