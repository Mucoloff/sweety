package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.server.cache.CacheKey;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import dev.sweety.versioning.server.injector.JarPatcher;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static dev.sweety.versioning.server.util.HttpUtils.*;

public class UpdateServer {

    private final HttpServer server;
    private final ReleaseManager releaseManager;
    private final CacheManager cacheManager;
    private final ClientRegistry clientRegistry;
    private final Gson gson = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final String rollbackToken;
    private final NotificationHub notificationHub;
    private final String publicBaseUrl;
    private final String websocketPublicUrl;

    public UpdateServer(int port,
                        ReleaseManager releaseManager,
                        CacheManager cacheManager,
                        ClientRegistry clientRegistry,
                        WebhookHandler webhookHandler,
                        String rollbackToken,
                        NotificationHub notificationHub,
                        String publicBaseUrl,
                        String websocketPublicUrl) throws Exception {
        this.releaseManager = releaseManager;
        this.cacheManager = cacheManager;
        this.clientRegistry = clientRegistry;
        this.rollbackToken = rollbackToken;
        this.notificationHub = notificationHub;
        this.publicBaseUrl = publicBaseUrl;
        this.websocketPublicUrl = websocketPublicUrl;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        this.server.createContext("/latest", new LatestHandler());
        this.server.createContext("/download", new DownloadHandler());
        this.server.createContext("/webhook", webhookHandler);
        this.server.createContext("/rollback", new RollbackHandler());

        this.server.setExecutor(Executors.newFixedThreadPool(16));
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    class LatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Method not allowed");
                    return;
                }

                ReleaseManager.VersionState state = releaseManager.current();
                JsonObject response = new JsonObject();
                response.addProperty("launcher", state.launcherVersion());
                response.addProperty("app", state.appVersion());
                response.addProperty("downloadLauncher", publicBaseUrl + "/download?artifact=launcher&version=" + state.launcherVersion());
                response.addProperty("downloadApp", publicBaseUrl + "/download?artifact=app&version=" + state.appVersion());
                response.addProperty("websocketUrl", websocketPublicUrl);
                sendJson(exchange, gson.toJson(response));
            } catch (Exception e) {
                sendText(exchange, 500, "latest error: " + e.getMessage());
            }
        }
    }

    class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Method not allowed");
                    return;
                }

                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                Artifact artifact = Artifact.valueOf(query.getOrDefault("artifact", "app"));
                UUID clientId = Utils.parseUuid(query.getOrDefault("clientId", "anonymous"));

                ReleaseManager.VersionState state = releaseManager.current();
                String version = query.get("version");
                if (version == null || version.isBlank()) {
                    version = "launcher".equalsIgnoreCase(artifact.name()) ? state.launcherVersion() : state.appVersion();
                }

                Path baseJar = releaseManager.resolveBaseJar(artifact.name(), version);
                if (!Files.exists(baseJar)) {
                    sendText(exchange, 404, "Base jar not found: " + baseJar);
                    return;
                }

                final Version finalVersion = Version.parse(version);
                CacheKey key = new CacheKey(artifact, finalVersion, clientId);
                byte[] data = cacheManager.getOrCreate(key, () -> {
                    Map<String, Object> fields = new HashMap<>(clientRegistry.buildPatchFields(clientId, finalVersion));
                    fields.put("VERSION", finalVersion);
                    byte[] patched = JarPatcher.patchJar(
                            baseJar,
                            clientId,
                            finalVersion,
                            fields,
                            clientRegistry.buildClientWatermarks(clientId, finalVersion)
                    );
                    System.out.println("Patched artifact=" + artifact + " clientId=" + clientId + " version=" + finalVersion + " bytes=" + patched.length);
                    return patched;
                });

                exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + finalVersion + "-" + clientId + ".jar\"");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } catch (Exception e) {
                sendText(exchange, 500, "download error: " + e.getMessage());
            }
        }
    }

    class RollbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendText(exchange, 405, "Method not allowed");
                    return;
                }
                if (!isAuthorizedRollback(exchange)) {
                    sendText(exchange, 401, "Unauthorized");
                    return;
                }
                boolean ok = releaseManager.rollback();
                if (!ok) {
                    sendText(exchange, 409, "No rollback version available");
                    return;
                }
                ReleaseManager.VersionState state = releaseManager.current();
                notificationHub.broadcastRelease(publicBaseUrl, state.launcherVersion(), state.appVersion());
                sendText(exchange, 200, "Rollback applied");
            } catch (Exception e) {
                sendText(exchange, 500, "rollback error: " + e.getMessage());
            }
        }

        private boolean isAuthorizedRollback(HttpExchange exchange) {
            if (rollbackToken == null || rollbackToken.isBlank()) {
                return false;
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                return false;
            }
            String token = auth.substring("Bearer ".length());
            return constantTimeEquals(token, rollbackToken);
        }
    }


}
