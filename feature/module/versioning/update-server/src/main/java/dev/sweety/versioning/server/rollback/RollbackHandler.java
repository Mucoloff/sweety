package dev.sweety.versioning.server.rollback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import lombok.Setter;

import java.io.IOException;
import java.util.function.BiConsumer;

import static dev.sweety.versioning.server.util.HttpUtils.constantTimeEquals;
import static dev.sweety.versioning.server.util.HttpUtils.sendText;

public class RollbackHandler implements HttpHandler {

    private final String rollbackToken;
    private final ReleaseManager releaseManager;

    @Setter
    private BiConsumer<ReleaseInfo, ReleaseInfo> broadcast;

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

            Artifact artifact = null; //todo read from query or body

            System.out.println("Received rollback Request:");

            final ReleaseInfo prev = releaseManager.latest(artifact);
            final ReleaseInfo rolled = releaseManager.rollback(artifact);
            final boolean ok = rolled != null;

            if (!ok) {
                sendText(exchange, 409, "No rollback version available");
                System.out.println("No rollback version available");
                return;
            }

            if (this.broadcast != null) this.broadcast.accept(rolled, prev);

            sendText(exchange, 200, "Rollback applied!");
            System.out.println("Rollback applied!");
        } catch (Exception e) {
            sendText(exchange, 500, "rollback error: " + e.getMessage());
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