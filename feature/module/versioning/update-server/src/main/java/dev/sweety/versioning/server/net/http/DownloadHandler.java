package dev.sweety.versioning.server.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.patch.format.archive.PatchArchiveMerger;
import dev.sweety.patch.model.type.PatchTypes;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.data.CacheKey;
import dev.sweety.versioning.server.store.CacheManager;
import dev.sweety.versioning.server.service.JarInjector;
import dev.sweety.versioning.server.data.PatchDefinition;
import dev.sweety.versioning.server.service.PatchManager;
import dev.sweety.versioning.server.data.ClientRegistry;
import dev.sweety.versioning.server.data.Token;
import dev.sweety.versioning.server.store.DownloadTokenStore;
import dev.sweety.versioning.server.store.DownloadSession;
import dev.sweety.versioning.server.store.DownloadSessionRegistry;
import dev.sweety.versioning.server.security.ArtifactSigner;
import dev.sweety.versioning.server.util.http.BandwidthLimiter;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.data.ObjectUtils;
import dev.sweety.versioning.version.ReleaseService;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DownloadHandler implements HttpHandler {
    private static final SimpleLogger LOGGER = SimpleLogger.of(DownloadHandler.class);

    private final DownloadTokenStore downloadManager;
    private final CacheManager cacheManager;
    private final ClientRegistry clientRegistry;
    private final ReleaseService releaseManager;
    private final PatchManager patchManager;
    private final BandwidthLimiter globalRateLimiter;
    private final ArtifactSigner signer;
    private final DownloadSessionRegistry sessions;

    public DownloadHandler(DownloadTokenStore downloadManager, CacheManager cacheManager, ClientRegistry clientRegistry, ReleaseService releaseManager, PatchManager patchManager, ArtifactSigner signer, DownloadSessionRegistry sessions) {
        this.downloadManager = downloadManager;
        this.cacheManager = cacheManager;
        this.clientRegistry = clientRegistry;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
        this.signer = signer;
        this.sessions = sessions;
        this.globalRateLimiter = BandwidthLimiter.perSecond(Settings.DOWNLOAD_SPEED);
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

            String jarName = artifact + "-" + version + "-" + channel + "-" + clientId + ".jar";

            switch (token.downloadType()) {
                case FULL -> sendBytes(exchange, _token, jarBytes, jarName);
                case PATCH -> {
                    Optional<Path> cached = this.patchManager.cached(artifact, key.channel(), key.version(), from);

                    if (cached.isEmpty()) {
                        sendBytes(exchange, _token, jarBytes, jarName);
                    } else {
                        Path file = this.patchManager.generatePatch(key, from);
                        PatchArchiveMerger.merge(file, cached.get(), file);

                        String patchName = artifact + "-" + version + "-" + channel + "-" + clientId + PatchTypes.PATCH_JAR.extension();
                        sendFile(exchange, _token, file, patchName);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Download processing failed", e);
            HttpUtils.sendText(exchange, 500, "download error");
        }
    }

    /** Sends an in-memory artifact, rate-limited, with integrity headers + a live progressive-hash session. */
    private void sendBytes(HttpExchange exchange, String token, byte[] data, String filename) throws IOException {
        setArtifactHeaders(exchange, filename, sha256(data));
        DownloadSession session = sessions.open(token, data.length);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            globalRateLimiter.transfer(data, os, session.sink());
        } finally {
            sessions.close(token);
        }
    }

    /** Streams a file artifact, rate-limited, with integrity headers (one prep pass) + live session. */
    private void sendFile(HttpExchange exchange, String token, Path file, String filename) throws IOException {
        setArtifactHeaders(exchange, filename, sha256(file));
        long size = Files.size(file);
        DownloadSession session = sessions.open(token, size);
        exchange.sendResponseHeaders(200, size);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
             OutputStream os = exchange.getResponseBody()) {
            globalRateLimiter.transfer(in, os, session.sink());
        } finally {
            sessions.close(token);
        }
    }

    /** Sets Content-* plus the three integrity headers (SHA-256 + HMAC + Ed25519, latter two if configured). */
    private void setArtifactHeaders(HttpExchange exchange, String filename, byte[] digest) {
        var h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/java-archive");
        h.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        h.set("X-Content-SHA256", toHex(digest));
        String hmac = signer.hmacHex(digest);
        if (hmac != null) h.set("X-Content-HMAC", hmac);
        String ed = signer.ed25519Base64(digest);
        if (ed != null) h.set("X-Content-Ed25519", ed);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = dev.sweety.data.buffer.BufferPool.DEFAULT.borrowBytes(64 * 1024);
            try {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    int n;
                    while ((n = in.read(buf, 0, buf.length)) != -1) md.update(buf, 0, n);
                }
                return md.digest();
            } finally {
                dev.sweety.data.buffer.BufferPool.DEFAULT.returnBytes(buf);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
