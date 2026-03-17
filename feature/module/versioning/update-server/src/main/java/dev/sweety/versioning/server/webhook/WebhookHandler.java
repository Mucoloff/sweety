package dev.sweety.versioning.server.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.server.util.Multipart;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import lombok.Setter;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dev.sweety.versioning.server.util.HttpUtils.sendText;
import static dev.sweety.versioning.server.util.HttpUtils.verifySignature;


public class WebhookHandler implements HttpHandler {

    private final String secret;
    private final ReleaseManager releaseManager;

    private final WebhookIdempotencyStore idempotencyStore;
    private final WebhookRateLimiter rateLimiter;

    @Setter
    private BiConsumer<Artifact, ReleaseInfo> broadcast;

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
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(ip)) {
                sendText(exchange, 429, "Rate limited");
                return;
            }

            String deliveryId = exchange.getRequestHeaders().getFirst("X-Delivery-Id");
            if (idempotencyStore.isProcessed(deliveryId)) {
                sendText(exchange, 202, "Already processed");
                return;
            }

            byte[] body = exchange.getRequestBody().readNBytes(50 * 1024 * 1024);

            if (!verifySignature(
                    secret,
                    exchange.getRequestHeaders().getFirst("X-Signature"),
                    body)) {

                sendText(exchange, 401, "Invalid signature");
                return;
            }

            idempotencyStore.mark(deliveryId);

            Multipart form = Multipart.parse(exchange, body);

            String art = form.getField("artifact");

            Artifact artifact;
            try {
                artifact = Artifact.valueOf(art);
            } catch (NullPointerException | IllegalArgumentException e) {
                sendText(exchange, 404, "Invalid artifact: " + art);
                return;
            }

            String channel = form.getField("channel");
            String version = form.getField("version");
            byte[] jar = form.getFile("jar");

            ReleaseInfo release = releaseManager.applyRelease(artifact, channel, version, jar);
            boolean updated = release != null;

            if (updated) {
                if (broadcast != null) this.broadcast.accept(artifact, release);
            }

            sendText(exchange, 200, updated ? "updated" : "no changes");
        } catch (Exception e) {
            sendText(exchange, 500, "Webhook error: "  + e.getMessage());
        } finally {

            exchange.close();

        }

    }

}