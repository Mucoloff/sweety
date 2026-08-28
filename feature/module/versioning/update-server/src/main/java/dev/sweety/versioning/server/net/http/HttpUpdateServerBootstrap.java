package dev.sweety.versioning.server.net.http;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.sweety.netty.tls.Tls;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.store.DownloadSessionRegistry;
import dev.sweety.versioning.server.security.ArtifactSigner;
import dev.sweety.versioning.server.store.ReleaseBroadcaster;
import dev.sweety.versioning.server.data.ArtifactRegistry;
import dev.sweety.versioning.server.store.CacheManager;
import dev.sweety.versioning.server.data.ClientRegistry;
import dev.sweety.versioning.server.service.PatchManager;
import dev.sweety.versioning.server.store.WebhookIdempotencyStore;
import dev.sweety.versioning.server.store.WebhookRateLimiter;
import dev.sweety.versioning.server.service.PublishReleaseUseCase;
import dev.sweety.versioning.server.service.RollbackReleaseUseCase;
import dev.sweety.versioning.server.store.DownloadTokenStore;
import dev.sweety.versioning.version.ReleaseService;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpUpdateServerBootstrap {

    private static final SimpleLogger LOGGER = SimpleLogger.of(HttpUpdateServerBootstrap.class);

    private final HttpServer server;
    private final RollbackHandler rollbackHandler;
    private final WebhookHandler webhookHandler;

    public HttpUpdateServerBootstrap(int port,
                                     final String rollbackToken,
                                     final ArtifactRegistry artifactRegistry,
                                     PublishReleaseUseCase publishUseCase,
                                     RollbackReleaseUseCase rollbackUseCase,
                                     ReleaseService releaseQuery,
                                     PatchManager patchManager,
                                     DownloadTokenStore downloadTokenStore,
                                     CacheManager cacheManager,
                                     ClientRegistry clientRegistry,
                                     ArtifactSigner signer,
                                     DownloadSessionRegistry downloadSessions) throws IOException {

        this.server = createServer(port);

        this.rollbackHandler = new RollbackHandler(rollbackToken, rollbackUseCase, releaseQuery);
        this.webhookHandler = new WebhookHandler(artifactRegistry, publishUseCase, patchManager, new WebhookIdempotencyStore(Settings.DEFAULT_TTL), new WebhookRateLimiter(Settings.RATE_LIMIT_WINDOW, Settings.GLOBAL_RATE_LIMIT, Settings.PER_IP_RATE_LIMIT));

        this.server.createContext("/download", new DownloadHandler(downloadTokenStore, cacheManager, clientRegistry, releaseQuery, patchManager, signer, downloadSessions));
        this.server.createContext("/release/latest", new LatestReleaseHttpHandler(releaseQuery));
        this.server.createContext("/release/base-jar", new BaseJarReleaseHttpHandler(releaseQuery));
        this.server.createContext("/release/download-token", new ReleaseDownloadTokenHandler(downloadTokenStore, releaseQuery));
        this.server.createContext("/rollback", this.rollbackHandler);
        this.server.createContext("/webhook", this.webhookHandler);
    }

    /**
     * Creates an {@link HttpsServer} reusing the Netty TLS cert (or a dev self-signed cert) when TLS is
     * enabled; otherwise enforces the shared plaintext policy and falls back to plain {@link HttpServer}.
     */
    private static HttpServer createServer(int port) throws IOException {
        final InetSocketAddress addr = new InetSocketAddress(port);
        try {
            if (Tls.devMode()) {
                HttpsServer https = HttpsServer.create(addr, 0);
                https.setHttpsConfigurator(new HttpsConfigurator(Tls.devHttpsServerContext()));
                LOGGER.warn("HTTP update server using DEV self-signed TLS — not for production");
                return https;
            }
            if (Tls.isEnabled()) {
                HttpsServer https = HttpsServer.create(addr, 0);
                https.setHttpsConfigurator(new HttpsConfigurator(Tls.httpsServerContext()));
                LOGGER.info("HTTP update server using TLS");
                return https;
            }
        } catch (javax.net.ssl.SSLException e) {
            throw new IOException("Failed to initialise HTTPS for update server", e);
        }
        Tls.enforceTlsPolicy("http-update-server");
        return HttpServer.create(addr, 0);
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

    public void setBroadcast(ReleaseBroadcaster broadcast) {
        this.webhookHandler.setBroadcast(broadcast);
        this.rollbackHandler.setBroadcast(broadcast);
    }
}
