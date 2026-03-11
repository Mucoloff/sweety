package dev.sweety.versioning.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseManagerBootstrapTest {

    private HttpServer jarServer;

    @AfterEach
    void teardown() {
        if (jarServer != null) {
            jarServer.stop(0);
        }
    }

    private String startJarServer(byte[] payload) throws IOException {
        jarServer = HttpServer.create(new InetSocketAddress(0), 0);
        jarServer.createContext("/artifact.jar", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        jarServer.start();
        return "http://localhost:" + jarServer.getAddress().getPort() + "/artifact.jar";
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}

