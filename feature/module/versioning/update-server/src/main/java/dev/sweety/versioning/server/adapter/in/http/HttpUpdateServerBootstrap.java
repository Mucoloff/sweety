package dev.sweety.versioning.server.adapter.in.http;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.port.out.ReleaseBroadcaster;
import dev.sweety.versioning.server.domain.artifact.ArtifactRegistry;
import dev.sweety.versioning.server.adapter.out.cache.CacheManager;
import dev.sweety.versioning.server.domain.client.ClientRegistry;
import dev.sweety.versioning.server.application.patch.PatchManager;
import dev.sweety.versioning.server.adapter.out.webhook.WebhookIdempotencyStore;
import dev.sweety.versioning.server.adapter.out.webhook.WebhookRateLimiter;
import dev.sweety.versioning.server.port.in.PublishReleaseUseCase;
import dev.sweety.versioning.server.port.in.RollbackReleaseUseCase;
import dev.sweety.versioning.server.port.out.DownloadTokenStore;
import dev.sweety.versioning.version.ReleaseService;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpUpdateServerBootstrap {

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
                                     ClientRegistry clientRegistry) throws IOException {

        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        this.rollbackHandler = new RollbackHandler(rollbackToken, rollbackUseCase, releaseQuery);
        this.webhookHandler = new WebhookHandler(artifactRegistry, publishUseCase, patchManager, new WebhookIdempotencyStore(Settings.DEFAULT_TTL), new WebhookRateLimiter(Settings.RATE_LIMIT_WINDOW, Settings.GLOBAL_RATE_LIMIT, Settings.PER_IP_RATE_LIMIT));

        this.server.createContext("/download", new DownloadHandler(downloadTokenStore, cacheManager, clientRegistry, releaseQuery, patchManager));
        this.server.createContext("/release/latest", new LatestReleaseHttpHandler(releaseQuery));
        this.server.createContext("/release/base-jar", new BaseJarReleaseHttpHandler(releaseQuery));
        this.server.createContext("/release/download-token", new ReleaseDownloadTokenHandler(downloadTokenStore, releaseQuery));
        this.server.createContext("/rollback", this.rollbackHandler);
        this.server.createContext("/webhook", this.webhookHandler);
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
