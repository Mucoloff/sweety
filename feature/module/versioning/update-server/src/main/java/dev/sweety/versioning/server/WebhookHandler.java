package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class WebhookHandler implements HttpHandler {

    private final String secret;
    private final ReleaseManager releaseManager;
    private final NotificationHub notificationHub;
    private final String publicBaseUrl;
    private final Gson gson = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final WebhookIdempotencyStore idempotencyStore;
    private final WebhookRateLimiter rateLimiter;

    public WebhookHandler(String secret,
                          ReleaseManager releaseManager,
                          NotificationHub notificationHub,
                          String publicBaseUrl,
                          WebhookIdempotencyStore idempotencyStore,
                          WebhookRateLimiter rateLimiter) {
        this.secret = secret;
        this.releaseManager = releaseManager;
        this.notificationHub = notificationHub;
        this.publicBaseUrl = publicBaseUrl;
        this.idempotencyStore = idempotencyStore;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void handle(HttpExchange exchange) throws java.io.IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allowRequest(remoteIp)) {
                sendText(exchange, 429, "Rate limited");
                return;
            }

            String deliveryId = exchange.getRequestHeaders().getFirst("X-GitHub-Delivery");
            if (idempotencyStore.isProcessed(deliveryId)) {
                sendText(exchange, 202, "Already processed");
                return;
            }

            byte[] body = exchange.getRequestBody().readAllBytes();
            String sig = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
            if (!isValidSignature(sig, body, secret)) {
                sendText(exchange, 401, "Invalid signature");
                return;
            }

            String eventType = exchange.getRequestHeaders().getFirst("X-GitHub-Event");
            JsonObject payload = gson.fromJson(new String(body, StandardCharsets.UTF_8), JsonObject.class);
            if (payload == null) {
                sendText(exchange, 400, "Invalid JSON payload");
                return;
            }

            if (eventType != null && !eventType.isBlank() && !"release".equalsIgnoreCase(eventType)) {
                sendText(exchange, 202, "Ignored event: " + eventType);
                return;
            }

            String action = payload.has("action") && !payload.get("action").isJsonNull() ? payload.get("action").getAsString() : "";
            boolean acceptedAction = action.isBlank()
                    || "published".equalsIgnoreCase(action)
                    || "released".equalsIgnoreCase(action)
                    || "prereleased".equalsIgnoreCase(action);
            if (!acceptedAction) {
                sendText(exchange, 202, "Ignored release action: " + action);
                return;
            }

            System.out.println("payload: " + payload);

            boolean updated = releaseManager.applyRelease(payload);
            idempotencyStore.mark(deliveryId);
            if (updated) {
                ReleaseManager.VersionState state = releaseManager.current();
                notificationHub.broadcastRelease(publicBaseUrl, state.launcherVersion(), state.appVersion());
            }
            sendText(exchange, 200, updated ? "Release updated" : "No changes");
        } catch (Exception e) {
            sendText(exchange, 500, "Webhook error: " + e.getMessage());
        }
    }

    static boolean isValidSignature(String signature, byte[] body, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body);
        String expected = "sha256=" + toHex(digest);
        return constantTimeEquals(expected, signature);
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}