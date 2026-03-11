package dev.sweety.versioning.server;

import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import dev.sweety.versioning.server.storage.Storage;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_PORT", "8080"));
        int wsPort = Integer.parseInt(System.getenv().getOrDefault("UPDATE_SERVER_WS_PORT", Integer.toString(port + 1)));
        String secret = System.getenv().getOrDefault("UPDATE_SERVER_SECRET", "");
        String rollbackToken = System.getenv().getOrDefault("UPDATE_SERVER_ROLLBACK_TOKEN", "change-me");

        String publicBaseUrl = System.getenv().getOrDefault("UPDATE_SERVER_PUBLIC_URL", "http://localhost:" + port);
        String websocketPublicUrl = System.getenv().getOrDefault("UPDATE_SERVER_WS_URL", "ws://localhost:" + wsPort + "/listen");

        Path root = Path.of(System.getenv().getOrDefault("UPDATE_SERVER_ROOT", "storage"));
        Path base = root.resolve("base");
        Path cache = root.resolve("cache");
        Path tmp = root.resolve("tmp");

        Files.createDirectories(base);
        Files.createDirectories(cache);
        Files.createDirectories(tmp);

        ReleaseManager releaseManager = new ReleaseManager(base);

        CacheManager cacheManager = new CacheManager(new Storage());
        ClientRegistry clientRegistry = new ClientRegistry();
        NotificationHub notificationHub = new NotificationHub();
        WebhookIdempotencyStore idempotencyStore = new WebhookIdempotencyStore();
        WebhookRateLimiter rateLimiter = new WebhookRateLimiter();
        WebhookHandler webhookHandler = new WebhookHandler(secret, releaseManager, notificationHub, publicBaseUrl, idempotencyStore, rateLimiter);

        UpdateServer server = new UpdateServer(port, releaseManager, cacheManager, clientRegistry, webhookHandler, rollbackToken, notificationHub, publicBaseUrl, websocketPublicUrl);
        WebSocketNotificationServer webSocketServer = new WebSocketNotificationServer(new InetSocketAddress(wsPort), notificationHub);

        webSocketServer.start();
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            try {
                webSocketServer.stop();
            } catch (Exception ignored) {
            }
        }));
        System.out.println("Update server started on port " + port + ", websocket on " + wsPort);
    }

}