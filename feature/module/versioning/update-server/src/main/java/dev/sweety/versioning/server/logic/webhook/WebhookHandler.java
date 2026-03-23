package dev.sweety.versioning.server.logic.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.actions.ReleaseConsumer;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.util.http.Multipart;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import lombok.Setter;

import java.io.IOException;

import static dev.sweety.versioning.server.util.http.HttpUtils.sendText;
import static dev.sweety.versioning.server.util.http.HttpUtils.verifySignature;


public class WebhookHandler implements HttpHandler {

    private final String secret;
    private final ReleaseManager releaseManager;

    private final WebhookIdempotencyStore idempotencyStore;
    private final WebhookRateLimiter rateLimiter;
    private final PatchManager patchManager;

    @Setter
    private ReleaseConsumer broadcast;

    public WebhookHandler(
            String secret,
            ReleaseManager releaseManager,
            PatchManager patchManager, WebhookIdempotencyStore idempotencyStore,
            WebhookRateLimiter rateLimiter
    ) {
        this.secret = secret;
        this.releaseManager = releaseManager;
        this.patchManager = patchManager;
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

            final String channel = form.getField("channel");
            final String version = form.getField("version");
            final String rollout = form.getField("rollout");
            final byte[] jar = form.getFile("jar");

            Float rolloutFloat;
            if (rollout != null && !rollout.isBlank()) {
                try {
                    rolloutFloat = Float.parseFloat(rollout.trim());
                    if (rolloutFloat < 0 || rolloutFloat > 1) {
                        sendText(exchange, 400, "Invalid rollout percentage: " + rollout);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    rolloutFloat = null;
                }

            } else rolloutFloat = null;

            ReleaseInfo release = releaseManager.applyRelease(artifact, channel, version, rolloutFloat, jar);
            boolean updated = release != null;

            if (updated) {
                this.patchManager.generatePatch(artifact, release.channel(), release.version());
                if (broadcast != null) this.broadcast.release(artifact, release);
            }

            sendText(exchange, 200, updated ? "updated" : "no changes");
        } catch (Exception e) {
            //todo remove all exceptions from requests
            sendText(exchange, 500, "Webhook error: " + e.getMessage());
        } finally {

            exchange.close();

        }

    }

}