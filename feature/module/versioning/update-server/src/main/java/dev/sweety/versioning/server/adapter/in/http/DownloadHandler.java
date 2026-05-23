package dev.sweety.versioning.server.adapter.in.http;

import com.google.common.util.concurrent.RateLimiter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.patch.format.archive.PatchArchiveMerger;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.adapter.out.cache.CacheKey;
import dev.sweety.versioning.server.adapter.out.cache.CacheManager;
import dev.sweety.versioning.server.application.patch.JarInjector;
import dev.sweety.versioning.server.application.patch.PatchDefinition;
import dev.sweety.versioning.server.application.patch.PatchManager;
import dev.sweety.versioning.server.domain.client.ClientRegistry;
import dev.sweety.versioning.server.domain.download.Token;
import dev.sweety.versioning.server.port.out.DownloadTokenStore;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.version.IReleaseService;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DownloadHandler implements HttpHandler {
    private static final SimpleLogger LOGGER = SimpleLogger.of(DownloadHandler.class);

    private final DownloadTokenStore downloadManager;
    private final CacheManager cacheManager;
    private final ClientRegistry clientRegistry;
    private final IReleaseService releaseManager;
    private final PatchManager patchManager;
    private final RateLimiter globalRateLimiter;

    public DownloadHandler(DownloadTokenStore downloadManager, CacheManager cacheManager, ClientRegistry clientRegistry, IReleaseService releaseManager, PatchManager patchManager) {
        this.downloadManager = downloadManager;
        this.cacheManager = cacheManager;
        this.clientRegistry = clientRegistry;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.globalRateLimiter = RateLimiter.create(Settings.DOWNLOAD_SPEED);
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
                id = ObjectUtils.parseUuid(_clientId);
            } catch (IllegalArgumentException e) {
                HttpUtils.sendText(exchange, 400, "Invalid clientId");
                return;
            }

            final Token token = this.downloadManager.consume(_token);
            if (token == null) {
                LOGGER.warn("Invalid or expired download token for clientId=" + id);
                HttpUtils.sendText(exchange, 400, "Invalid or expired token");
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
                LOGGER.info("Patched artifact=" + k.artifact() + " clientId=" + k.clientId() + " version=" + k.version() + " bytes=" + patched.length);
                return patched;
            });

            byte[] data = switch (token.downloadType()) {
                case FULL -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + ".jar\"");
                    yield jarBytes;
                }
                case PATCH -> {
                    Optional<Path> cached = this.patchManager.cached(artifact, key.channel(), key.version(), from);

                    if (cached.isEmpty()) {
                        exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + ".jar\"");
                        yield jarBytes;
                    }

                    Path file = this.patchManager.generatePatch(key, from);

                    PatchArchiveMerger.merge(file, cached.get(), file);

                    String patchSuffix = PatchTypes.PATCH_JAR.extension();
                    exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + artifact + "-" + version + "-" + channel + "-" + clientId + patchSuffix + "\"");
                    yield Files.readAllBytes(file);
                }
            };

            exchange.sendResponseHeaders(200, data.length);

            try (OutputStream os = exchange.getResponseBody()) {
                int offset = 0;

                while (offset < data.length) {
                    int len = Math.min(1024, data.length - offset);

                    globalRateLimiter.acquire(len);

                    os.write(data, offset, len);
                    offset += len;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Download processing failed", e);
            HttpUtils.sendText(exchange, 500, "download error");
        }
    }
}
