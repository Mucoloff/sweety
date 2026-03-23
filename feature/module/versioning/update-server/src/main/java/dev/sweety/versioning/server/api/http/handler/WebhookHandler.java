package dev.sweety.versioning.server.api.http.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.server.logic.actions.ReleaseBroadcastConsumer;
import dev.sweety.versioning.server.logic.patch.PatchManager;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.logic.webhook.WebhookIdempotencyStore;
import dev.sweety.versioning.server.logic.webhook.WebhookRateLimiter;
import dev.sweety.versioning.server.util.http.Multipart;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import lombok.Setter;

import java.io.IOException;

import static dev.sweety.versioning.server.util.http.HttpUtils.sendText;
import static dev.sweety.versioning.server.util.http.HttpUtils.verifySignature;


public class WebhookHandler implements HttpHandler {
    private static final SimpleLogger LOGGER = new SimpleLogger(WebhookHandler.class);

    private final String secret;
    private final ReleaseManager releaseManager;

    private final WebhookIdempotencyStore idempotencyStore;
    private final WebhookRateLimiter rateLimiter;
    private final PatchManager patchManager;

    @Setter
    private ReleaseBroadcastConsumer broadcast;

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
                LOGGER.warn("Webhook rate limited for ip=" + ip);
                sendText(exchange, 429, "Rate limited");
                return;
            }

            String deliveryId = exchange.getRequestHeaders().getFirst("X-Delivery-Id");
            if (idempotencyStore.isProcessed(deliveryId)) {
                LOGGER.info("Webhook duplicate delivery: " + deliveryId);
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
                LOGGER.warn("Invalid webhook artifact value: " + art);
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

            ReleaseInfo release;
            if ((version == null || version.isBlank()) && rolloutFloat != null) {
                Channel parsedChannel;
                try {
                    parsedChannel = Channel.valueOf(channel.toUpperCase());
                } catch (NullPointerException | IllegalArgumentException e) {
                    LOGGER.warn("Invalid webhook rollout channel value: " + channel);
                    sendText(exchange, 400, "Invalid channel: " + channel);
                    return;
                }

                release = releaseManager.updateRollout(artifact, parsedChannel, rolloutFloat);
            } else {
                release = releaseManager.applyRelease(artifact, channel, version, rolloutFloat, jar);
            }
            boolean updated = release != null;

            if (updated) {
                this.patchManager.generatePatch(artifact, release.channel(), release.version());
                if (broadcast != null) {
                    this.broadcast.broadcast(artifact, release, release.channel(), ReleaseBroadcastType.NORMAL, null);
                }
                LOGGER.info("Webhook release updated: artifact=" + artifact + ", target=" + release);
            }

            sendText(exchange, 200, updated ? "updated" : "no changes");
        } catch (Exception e) {
            LOGGER.error("Webhook processing failed", e);
            sendText(exchange, 500, "Internal server error");
        } finally {

            exchange.close();

        }

    }

}
