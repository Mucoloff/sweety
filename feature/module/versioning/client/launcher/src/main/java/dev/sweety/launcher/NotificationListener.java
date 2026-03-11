package dev.sweety.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NotificationListener {

    public interface NotificationHandler {
        void onNotification(String launcherVersion, String appVersion);
    }

    private static final Gson GSON = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final String websocketUrl;
    private final String clientId;
    private final String launcherVersion;
    private final String appVersion;
    private final NotificationHandler handler;
    private final HttpClient httpClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile WebSocket socket;

    public NotificationListener(String websocketUrl,
                                String clientId,
                                String launcherVersion,
                                String appVersion,
                                NotificationHandler handler) {
        this.websocketUrl = Objects.requireNonNull(websocketUrl, "websocketUrl");
        this.clientId = Objects.requireNonNullElse(clientId, "anonymous");
        this.launcherVersion = Objects.requireNonNullElse(launcherVersion, "unknown");
        this.appVersion = Objects.requireNonNullElse(appVersion, "unknown");
        this.handler = handler;
        this.httpClient = HttpClient.newHttpClient();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WsNotificationReconnect-" + this.clientId);
            t.setDaemon(true);
            return t;
        });
    }

    public void startListening() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        connect(0);
    }

    public void stopListening() {
        started.set(false);
        WebSocket current = socket;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
            }
        }
        reconnectExecutor.shutdownNow();
    }

    private void connect(long delaySeconds) {
        reconnectExecutor.schedule(() -> {
            if (!started.get()) {
                return;
            }
            try {
                httpClient.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(URI.create(websocketUrl), new WsListener())
                        .whenComplete((ws, err) -> {
                            if (err != null) {
                                System.err.println("WebSocket connect error: " + err.getMessage());
                                scheduleReconnect();
                                return;
                            }
                            socket = ws;
                        });
            } catch (Exception e) {
                System.err.println("WebSocket setup error: " + e.getMessage());
                scheduleReconnect();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void scheduleReconnect() {
        if (started.get()) {
            connect(3);
        }
    }

    private void sendRegister(WebSocket ws) {
        JsonObject register = new JsonObject();
        register.addProperty("type", "register");
        register.addProperty("clientId", clientId);
        register.addProperty("launcher", launcherVersion);
        register.addProperty("app", appVersion);
        register.addProperty("contact", clientId + "@launcher");
        ws.sendText(register.toString(), true);
    }

    private void parseAndNotify(String json) {
        JsonObject payload = GSON.fromJson(json, JsonObject.class);
        System.out.println("received notify: " + json);
        if (payload == null || !payload.has("type")) return;

        String type = payload.get("type").getAsString();
        if (!"release".equalsIgnoreCase(type)) return;

        String lVersion = payload.has("launcher") && !payload.get("launcher").isJsonNull()
                ? payload.get("launcher").getAsString()
                : null;
        String aVersion = payload.has("app") && !payload.get("app").isJsonNull()
                ? payload.get("app").getAsString()
                : null;

        if (lVersion != null || aVersion != null) {
            System.out.println("Received notification: launcher=" + lVersion + ", app=" + aVersion);
            if (handler != null) {
                handler.onNotification(lVersion, aVersion);
            }
        }
    }

    private class WsListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            sendRegister(webSocket);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                System.out.println("received message: " + msg);
                try {
                    parseAndNotify(msg);
                } catch (Exception e) {
                    System.err.println("WebSocket message parse error: " + e.getMessage());
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket listener error: " + error.getMessage());
            scheduleReconnect();
        }
    }
}
