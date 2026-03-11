package dev.sweety.versioning.server;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookE2EIntegrationTest {

    @Test
    void webhookWithFakeAssetServerDownloadsPatchesAndCaches() throws Exception {
        Path root = Files.createTempDirectory("webhook-e2e-");
        Path base = root.resolve("base");
        Path cache = root.resolve("cache");
        Files.createDirectories(base);
        Files.createDirectories(cache);

        ReleaseManager releaseManager = new ReleaseManager(base);
        CacheManager cacheManager = new CacheManager(cache);
        ClientRegistry clientRegistry = new ClientRegistry();
        WebhookIdempotencyStore idempotencyStore = new WebhookIdempotencyStore();
        WebhookRateLimiter rateLimiter = new WebhookRateLimiter();
        NotificationHub notificationHub = new NotificationHub();
        WebhookHandler webhookHandler = new WebhookHandler(
                "",
                releaseManager,
                notificationHub,
                "http://localhost:8080",
                idempotencyStore,
                rateLimiter
        );

        UpdateServer server = new UpdateServer(
                0,
                releaseManager,
                cacheManager,
                clientRegistry,
                webhookHandler,
                "test-token",
                notificationHub,
                "http://localhost:8080",
                "ws://localhost:8080/listen"
        );
        server.start();

        FakeAssetServer fakeAssets = new FakeAssetServer(0);
        fakeAssets.start();

        try {
            int updatePort = server.port();
            int assetPort = fakeAssets.port();

            try (HttpClient http = HttpClient.newHttpClient()) {
                String launcherUrl = "http://localhost:" + assetPort + "/launcher-1.5.0.jar";
                String appUrl = "http://localhost:" + assetPort + "/app-1.5.0.jar";

                String webhookPayload = """
                        {
                          "action": "published",
                          "release": {
                            "tag_name": "v1.5.0",
                            "assets": [
                              {"name": "launcher-1.5.0.jar", "browser_download_url": "%s"},
                              {"name": "app-1.5.0.jar", "browser_download_url": "%s"}
                            ]
                          }
                        }
                        """.formatted(launcherUrl, appUrl);

                HttpRequest webhookReq = HttpRequest.newBuilder(URI.create("http://localhost:" + updatePort + "/webhook"))
                        .header("X-GitHub-Event", "release")
                        .header("X-GitHub-Delivery", "test-delivery-123")
                        .POST(HttpRequest.BodyPublishers.ofString(webhookPayload))
                        .build();

                HttpResponse<String> webhookRes = http.send(webhookReq, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, webhookRes.statusCode());
                assertTrue(webhookRes.body().contains("Release updated"));

                Thread.sleep(100);

                HttpRequest latestReq = HttpRequest.newBuilder(URI.create("http://localhost:" + updatePort + "/latest"))
                        .GET()
                        .build();
                HttpResponse<String> latestRes = http.send(latestReq, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, latestRes.statusCode());
                assertTrue(latestRes.body().contains("1.5.0"));

                HttpRequest downloadReq = HttpRequest.newBuilder(
                                URI.create("http://localhost:" + updatePort + "/download?artifact=app&clientId=testClient&version=1.5.0"))
                        .GET()
                        .build();
                HttpResponse<byte[]> downloadRes = http.send(downloadReq, HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, downloadRes.statusCode());
                assertTrue(downloadRes.body().length > 0);

                HttpRequest idempotentReq = HttpRequest.newBuilder(URI.create("http://localhost:" + updatePort + "/webhook"))
                        .header("X-GitHub-Event", "release")
                        .header("X-GitHub-Delivery", "test-delivery-123")
                        .POST(HttpRequest.BodyPublishers.ofString(webhookPayload))
                        .build();
                HttpResponse<String> idempotentRes = http.send(idempotentReq, HttpResponse.BodyHandlers.ofString());
                assertEquals(202, idempotentRes.statusCode());
                assertTrue(idempotentRes.body().contains("Already processed"));
            }
        } finally {
            server.stop(0);
            fakeAssets.stop(0);
        }
    }

    static class FakeAssetServer {

        private final HttpServer server;

        FakeAssetServer(int port) throws Exception {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            this.server.createContext("/", exchange -> {
                try {
                    byte[] jarBytes = createMinimalJar();
                    exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                    exchange.sendResponseHeaders(200, jarBytes.length);
                    try (var os = exchange.getResponseBody()) {
                        os.write(jarBytes);
                    }
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, -1);
                }
            });
            this.server.setExecutor(Executors.newFixedThreadPool(2));
        }

        void start() {
            server.start();
        }

        void stop(@SuppressWarnings("unused") int delay) {
            server.stop(0);
        }

        int port() {
            return server.getAddress().getPort();
        }

        private byte[] createMinimalJar() throws Exception {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
                JarEntry entry = new JarEntry("placeholder.txt");
                jos.putNextEntry(entry);
                jos.write("test".getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
            return baos.toByteArray();
        }
    }
}
