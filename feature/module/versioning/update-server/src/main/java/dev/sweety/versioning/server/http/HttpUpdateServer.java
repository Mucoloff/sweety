package dev.sweety.versioning.server.http;

import com.sun.net.httpserver.HttpServer;
import dev.sweety.versioning.server.cache.CacheManager;
import dev.sweety.versioning.server.client.ClientRegistry;
import dev.sweety.versioning.server.download.DownloadHandler;
import dev.sweety.versioning.server.download.DownloadManager;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.server.rollback.RollbackHandler;
import dev.sweety.versioning.server.webhook.WebhookHandler;
import dev.sweety.versioning.server.webhook.WebhookIdempotencyStore;
import dev.sweety.versioning.server.webhook.WebhookRateLimiter;
import dev.sweety.versioning.version.LatestInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

public class HttpUpdateServer {

    private final HttpServer server;
    private final RollbackHandler rollbackHandler;
    private final WebhookHandler webhookHandler;

    public HttpUpdateServer(int port,
                            final String rollbackToken,
                            final String webhookSecret,
                            ReleaseManager releaseManager,
                            DownloadManager downloadManager,
                            CacheManager cacheManager,
                            ClientRegistry clientRegistry) throws IOException {

        this.server = HttpServer.create(new InetSocketAddress(port), 0);


        this.rollbackHandler = new RollbackHandler(rollbackToken, releaseManager);
        this.webhookHandler = new WebhookHandler(webhookSecret, releaseManager, new WebhookIdempotencyStore(), new WebhookRateLimiter());

        this.server.createContext("/download", new DownloadHandler(downloadManager, cacheManager, clientRegistry, releaseManager));
        this.server.createContext("/rollback", this.rollbackHandler);
        this.server.createContext("/webhook", this.webhookHandler);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
    }

    public void setBroadcast(Consumer<LatestInfo> broadcast) {
        this.rollbackHandler.setBroadcast(broadcast);
        this.webhookHandler.setBroadcast(broadcast);
    }

    public int port() {
        return server.getAddress().getPort();
    }
}
