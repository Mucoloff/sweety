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
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;

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

    private ReleaseBroadcastConsumer broadcast;

    public WebhookHandler setBroadcast(ReleaseBroadcastConsumer broadcast) {
        this.broadcast = broadcast;
        return this;
    }

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

            final String channelStr = form.getField("channel");
            final String versionStr = form.getField("version");
            final String rollout = form.getField("rollout");
            final byte[] jar = form.getFile("jar");

            final Channel channel;
            try {
                if (channelStr == null || channelStr.isBlank()) {
                    throw new IllegalArgumentException("Channel is required");
                }
                channel = Channel.valueOf(channelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid webhook channel value: " + channelStr);
                sendText(exchange, 400, "Invalid channel: " + channelStr);
                return;
            }

            final Version version;
            try {
                if (versionStr != null && !versionStr.isBlank()) {
                    version = Version.parse(versionStr);
                } else {
                    version = null;
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Invalid webhook version value: " + versionStr);
                sendText(exchange, 400, "Invalid version: " + versionStr);
                return;
            }

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

            final ReleaseInfo release = version != null || rolloutFloat == null ? releaseManager.applyRelease(artifact, channel, version, rolloutFloat, jar) : releaseManager.updateRollout(artifact, channel, rolloutFloat);
            boolean updated = release != null;

            if (updated) {
                this.patchManager.generatePatch(artifact, release.channel(), release.version());
                if (broadcast != null)
                    this.broadcast.broadcast(artifact, release, release.channel(), ReleaseBroadcastType.NORMAL, null);
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
