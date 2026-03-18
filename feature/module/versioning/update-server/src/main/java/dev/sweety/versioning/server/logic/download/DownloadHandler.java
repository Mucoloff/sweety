package dev.sweety.versioning.server.logic.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.patch.format.PatchEditor;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.versioning.exception.*;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.cache.CacheKey;
import dev.sweety.versioning.server.logic.cache.CacheManager;
import dev.sweety.versioning.server.logic.client.ClientRegistry;
import dev.sweety.versioning.server.logic.patch.JarInjector;
import dev.sweety.versioning.server.logic.patch.PatchDefinition;
import dev.sweety.versioning.server.logic.token.Token;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DownloadHandler implements HttpHandler {

    private final DownloadManager downloadManager;
    private final CacheManager cacheManager;
    private final ClientRegistry clientRegistry;
    private final ReleaseManager releaseManager;
    private final PatchManager patchManager;

    public DownloadHandler(DownloadManager downloadManager, CacheManager cacheManager, ClientRegistry clientRegistry, ReleaseManager releaseManager, PatchManager patchManager) {
        this.downloadManager = downloadManager;
        this.cacheManager = cacheManager;
        this.clientRegistry = clientRegistry;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
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
                HttpUtils.sendText(exchange, 404, "Invalid clientId or token");
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
                System.out.println("error: " + e.getMessage());
                HttpUtils.sendText(exchange, 400, "Invalid or expired token " + e);
                return;
            }

            final UUID clientId = token.clientId();

            if (!id.equals(clientId)) {
                HttpUtils.sendText(exchange, 404, "clientId does not match token");
                return;
            }

            final Version version = token.version();
            final Version from = token.from();
            final Artifact artifact = token.artifact();
            final Channel channel = token.channel();

            Path baseJar = releaseManager.resolveBaseJar(artifact, channel, version);
            if (!Files.exists(baseJar)) {
                HttpUtils.sendText(exchange, 404, "Base jar not found: " + baseJar);
                return;
            }

            final CacheKey key = new CacheKey(artifact, channel, version, clientId);
            byte[] jarBytes = cacheManager.getOrCreate(key, k -> {
                PatchDefinition patch = clientRegistry.createPatchDefinition(k);
                byte[] patched = JarInjector.inject(
                        baseJar,
                        patch
                );
                System.out.println("Patched artifact=" + k.artifact() + " clientId=" + k.clientId() + " version=" + k.version() + " bytes=" + patched.length);
                return patched;
            });

            byte[] data = switch (token.downloadType()) {
                case FULL -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + ".jar\"");
                    yield jarBytes;
                }
                case PATCH -> {
                    Optional<File> cached = this.patchManager.cached(artifact, key.channel(), key.version(), from);

                    if (cached.isEmpty()) {
                        exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + ".jar\"");
                        yield jarBytes;
                    }

                    File file = this.patchManager.generatePatch(key, from);

                    PatchEditor editor = new PatchEditor(PatchTypes.JSON);

                    Patch jarPatch = editor.read(cached.get());

                    editor.edit(file, patch -> patch.getOperations().addAll(jarPatch.getOperations()));

                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + ".patch\"");
                    yield Files.readAllBytes(file.toPath());
                }
            };


            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtils.sendText(exchange, 500, "download error: " + e.getMessage());
        }
    }
}
