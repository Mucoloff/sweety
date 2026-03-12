package dev.sweety.versioning.server.rollback;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.sweety.versioning.server.release.ReleaseManager;
import dev.sweety.versioning.server.util.HttpUtils;
import dev.sweety.versioning.version.LatestInfo;
import lombok.Setter;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RollbackHandler implements HttpHandler {

    private final String rollbackToken;
    private final ReleaseManager releaseManager;

    @Setter
    private BiConsumer<LatestInfo, Boolean> broadcast;

    public RollbackHandler(String rollbackToken, ReleaseManager releaseManager) {
        this.rollbackToken = rollbackToken;
        this.releaseManager = releaseManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.sendText(exchange, 405, "Method not allowed");
                return;
            }
            if (!isAuthorizedRollback(exchange)) {
                HttpUtils.sendText(exchange, 401, "Unauthorized");
                return;
            }
            System.out.println("Received rollback Request:");
            final boolean ok = releaseManager.rollback();
            final LatestInfo state = releaseManager.latest();

            if (!ok) {
                HttpUtils.sendText(exchange, 409, "No rollback version available");
                System.out.println("No rollback version available");
                return;
            }

            if (this.broadcast != null) this.broadcast.accept(state, true);

            HttpUtils.sendText(exchange, 200, "Rollback applied!");
            System.out.println("Rollback applied!");
        } catch (Exception e) {
            HttpUtils.sendText(exchange, 500, "rollback error: " + e.getMessage());
        }
    }

    private boolean isAuthorizedRollback(HttpExchange exchange) {
        if (rollbackToken == null || rollbackToken.isBlank()) return false;

        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return false;
        String token = auth.substring("Bearer ".length());
        return HttpUtils.constantTimeEquals(token, rollbackToken);
    }
}