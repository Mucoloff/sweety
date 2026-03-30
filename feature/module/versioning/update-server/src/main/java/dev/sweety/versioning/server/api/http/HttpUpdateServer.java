package dev.sweety.versioning.server.api.http;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.server.Settings;
import dev.sweety.versioning.server.api.http.handler.RollbackHandler;
import dev.sweety.versioning.server.api.http.handler.WebhookHandler;
import dev.sweety.versioning.server.logic.cache.CacheManager;
import dev.sweety.versioning.server.logic.client.ClientRegistry;
import dev.sweety.versioning.server.logic.download.DownloadHandler;
import dev.sweety.versioning.server.logic.download.DownloadManager;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.actions.ReleaseBroadcastConsumer;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.webhook.WebhookIdempotencyStore;
import dev.sweety.versioning.server.logic.webhook.WebhookRateLimiter;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HttpUpdateServer {

    private final HttpServer server;
    private final RollbackHandler rollbackHandler;
    private final WebhookHandler webhookHandler;

    public HttpUpdateServer(int port,
                            final String rollbackToken,
                            final String webhookSecret,
                            ReleaseManager releaseManager,
                            PatchManager patchManager,
                            DownloadManager downloadManager,
                            CacheManager cacheManager,
                            ClientRegistry clientRegistry) throws IOException {

        this.server = HttpServer.create(new InetSocketAddress(port), 0);


        this.rollbackHandler = new RollbackHandler(rollbackToken, releaseManager);
        this.webhookHandler = new WebhookHandler(webhookSecret, releaseManager, patchManager, new WebhookIdempotencyStore(Settings.DEFAULT_TTL), new WebhookRateLimiter(Settings.RATE_LIMIT_WINDOW, Settings.GLOBAL_RATE_LIMIT, Settings.PER_IP_RATE_LIMIT));

        this.server.createContext("/download", new DownloadHandler(downloadManager, cacheManager, clientRegistry, releaseManager, patchManager));
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

    public void setBroadcast(ReleaseBroadcastConsumer broadcast) {
        this.webhookHandler.setBroadcast(broadcast);
        this.rollbackHandler.setBroadcast(broadcast);
    }
}
