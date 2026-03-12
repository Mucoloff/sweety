package dev.sweety.versioning.server.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.release.ReleaseManager;

import java.io.IOException;

import dev.sweety.versioning.server.util.HttpUtils;
import dev.sweety.versioning.server.util.Multipart;
import dev.sweety.versioning.version.LatestInfo;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


public class WebhookHandler implements HttpHandler {

    private final String secret;
    private final ReleaseManager releaseManager;

    private final WebhookIdempotencyStore idempotencyStore;
    private final WebhookRateLimiter rateLimiter;

    @Setter
    private BiConsumer<LatestInfo, Boolean> broadcast;

    public WebhookHandler(
            String secret,
            ReleaseManager releaseManager,
            WebhookIdempotencyStore idempotencyStore,
            WebhookRateLimiter rateLimiter
    ) {
        this.secret = secret;
        this.releaseManager = releaseManager;
        this.idempotencyStore = idempotencyStore;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        try {

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.sendText(exchange, 405, "Method not allowed");
                return;
            }

            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(ip)) {
                HttpUtils.sendText(exchange, 429, "Rate limited");
                return;
            }

            String deliveryId = exchange.getRequestHeaders().getFirst("X-Delivery-Id");
            if (idempotencyStore.isProcessed(deliveryId)) {
                HttpUtils.sendText(exchange, 202, "Already processed");
                return;
            }

            byte[] body = exchange.getRequestBody().readNBytes(50 * 1024 * 1024);

            if (!HttpUtils.verifySignature(
                    secret,
                    exchange.getRequestHeaders().getFirst("X-Signature"),
                    body)) {

                HttpUtils.sendText(exchange, 401, "Invalid signature");
                return;
            }

            idempotencyStore.mark(deliveryId);

            Multipart form = Multipart.parse(exchange, body);

            String launcherVersion = form.getField("launcherVersion");
            byte[] launcherJar = form.getFile("launcherJar");

            String appVersion = form.getField("appVersion");
            byte[] appJar = form.getFile("appJar");

            boolean updated = releaseManager.applyRelease(
                    launcherVersion,
                    launcherJar,
                    appVersion,
                    appJar
            );

            if (updated) {
                if (broadcast != null) {
                    this.broadcast.accept(releaseManager.latest(), false);
                }
            }

            HttpUtils.sendText(exchange, 200, updated ? "updated" : "no changes");
        } catch (Exception e) {
            HttpUtils.sendText(exchange, 500, "Webhook error");
        } finally {

            exchange.close();

        }

    }

}