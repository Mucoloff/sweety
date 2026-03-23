package dev.sweety.versioning.server.api.http.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.util.logger.SimpleLogger;
import dev.sweety.versioning.protocol.update.ReleaseBroadcastType;
import dev.sweety.versioning.server.logic.actions.ReleaseBroadcastConsumer;
import dev.sweety.versioning.server.logic.release.ReleaseManager;
import dev.sweety.versioning.server.util.http.HttpUtils;
import dev.sweety.versioning.server.util.http.Multipart;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.artifact.Artifact;
import dev.sweety.versioning.version.channel.Channel;
import lombok.Setter;

import java.io.IOException;

import static dev.sweety.versioning.server.util.http.HttpUtils.constantTimeEquals;
import static dev.sweety.versioning.server.util.http.HttpUtils.sendText;

public class RollbackHandler implements HttpHandler {
    private static final SimpleLogger LOGGER = new SimpleLogger(RollbackHandler.class);

    private final String rollbackToken;
    private final ReleaseManager releaseManager;

    @Setter
    private ReleaseBroadcastConsumer broadcast;

    public RollbackHandler(String rollbackToken, ReleaseManager releaseManager) {
        this.rollbackToken = rollbackToken;
        this.releaseManager = releaseManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            if (!isAuthorizedRollback(exchange)) {
                sendText(exchange, 401, "Unauthorized");
                return;
            }

            byte[] body = exchange.getRequestBody().readNBytes(50 * 1024 * 1024);

            Multipart form = Multipart.parse(exchange, body);

            String _artifact = form.getField("artifact");
            String _channel = form.getField("channel");

            if (_artifact == null || _artifact.isBlank() || _channel == null || _channel.isBlank()) {
                HttpUtils.sendText(exchange, 400, "Missing artifact or channel");
                return;
            }

            Artifact artifact;
            Channel channel;
            try {
                artifact = Artifact.valueOf(_artifact.toUpperCase());
                channel = Channel.valueOf(_channel.toUpperCase());
            } catch (NullPointerException | IllegalArgumentException e) {
                LOGGER.warn("Invalid rollback request: artifact=" + _artifact + ", channel=" + _channel);
                HttpUtils.sendText(exchange, 404, "Invalid artifact or channel");
                return;
            }

            final ReleaseInfo prev = releaseManager.latest(artifact, channel);
            final ReleaseInfo rolled = releaseManager.rollback(artifact, channel);
            final boolean ok = rolled != null;

            if (!ok) {
                sendText(exchange, 409, "No rollback version available");
                LOGGER.warn("Rollback requested but no history available for artifact=" + artifact + ", channel=" + channel);
                return;
            }

            if (this.broadcast != null) {
                this.broadcast.broadcast(artifact, rolled, channel, ReleaseBroadcastType.ROLLBACK, prev);
            }

            sendText(exchange, 200, "Rollback applied!");
            LOGGER.info("Rollback applied: artifact=" + artifact + ", from=" + prev + ", to=" + rolled);
        } catch (Exception e) {
            LOGGER.error("Rollback processing failed", e);
            sendText(exchange, 500, "Internal server error");
        }
    }

    private boolean isAuthorizedRollback(HttpExchange exchange) {
        if (rollbackToken == null || rollbackToken.isBlank()) return false;

        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return false;
        String token = auth.substring("Bearer ".length());
        return constantTimeEquals(token, rollbackToken);
    }
}
