package dev.sweety.versioning.server.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.exception.InvalidTokenException;
import dev.sweety.versioning.exception.TokenExpiredException;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.server.cache.CacheKey;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import dev.sweety.versioning.server.injector.JarPatcher;
import dev.sweety.versioning.server.token.Token;
import dev.sweety.versioning.server.util.HttpUtils;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.Version;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DownloadHandler implements HttpHandler {

    private final DownloadManager downloadManager;
    private final CacheManager cacheManager;
    private final ClientRegistry clientRegistry;
    private final ReleaseManager releaseManager;

    public DownloadHandler(DownloadManager downloadManager, CacheManager cacheManager, ClientRegistry clientRegistry, ReleaseManager releaseManager) {
        this.downloadManager = downloadManager;
        this.cacheManager = cacheManager;
        this.clientRegistry = clientRegistry;
        this.releaseManager = releaseManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.sendText(exchange, 405, "Method not allowed");
                return;
            }

            Map<String, String> query = HttpUtils.parseQuery(exchange.getRequestURI().getRawQuery());

            String _clientId = query.getOrDefault("clientId", "invalid");
            String _token = query.getOrDefault("token", "invalid");

            if (_clientId.isBlank() || _token.isBlank()) {
                HttpUtils.sendText(exchange, 400, "Missing clientId or token");
                return;
            }

            if (_clientId.equalsIgnoreCase("invalid") || _token.equalsIgnoreCase("invalid")) {
                HttpUtils.sendText(exchange, 400, "Invalid clientId or token");
                return;
            }

            final UUID id;
            try {
                id = Utils.parseUuid(_clientId);
            } catch (IllegalArgumentException e) {
                HttpUtils.sendText(exchange, 400, "Invalid clientId");
                return;
            }

            final Token token;
            try {
                token = this.downloadManager.search(_token);
            } catch (InvalidTokenException | TokenExpiredException e) {
                HttpUtils.sendText(exchange, 400, "Invalid or expired token");
                return;
            }

            final UUID clientId = token.clientId();

            if (!id.equals(clientId)) {
                HttpUtils.sendText(exchange, 400, "clientId does not match token");
                return;
            }

            final Version version = token.version();
            final Artifact artifact = token.type();

            Path baseJar = releaseManager.resolveBaseJar(artifact, version);
            if (!Files.exists(baseJar)) {
                HttpUtils.sendText(exchange, 404, "Base jar not found: " + baseJar);
                return;
            }

            CacheKey key = new CacheKey(artifact, version, clientId);
            byte[] data = cacheManager.getOrCreate(key, () -> {
                Map<String, Object> fields = new HashMap<>(clientRegistry.buildPatchFields(clientId, version));
                fields.put("VERSION", version);
                byte[] patched = JarPatcher.patchJar(
                        baseJar,
                        clientId,
                        version,
                        fields,
                        clientRegistry.buildClientWatermarks(clientId, version)
                );
                System.out.println("Patched artifact=" + artifact + " clientId=" + clientId + " version=" + version + " bytes=" + patched.length);
                return patched;
            });

            exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + clientId + ".jar\"");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            HttpUtils.sendText(exchange, 500, "download error: " + e.getMessage());
        }
    }
}
