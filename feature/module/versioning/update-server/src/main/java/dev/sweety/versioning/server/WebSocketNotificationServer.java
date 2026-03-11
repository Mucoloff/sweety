package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class WebSocketNotificationServer extends WebSocketServer {

    private final NotificationHub notificationHub;
    private final Gson gson = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public WebSocketNotificationServer(InetSocketAddress address, NotificationHub notificationHub) {
        super(address);
        this.notificationHub = notificationHub;
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("WebSocket client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        notificationHub.unregisterSocket(conn);
        System.out.println("WebSocket client disconnected: code=" + code + " reason=" + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject payload = gson.fromJson(message, JsonObject.class);
            if (payload == null) {
                return;
            }
            String type = payload.has("type") ? payload.get("type").getAsString() : "";
            if (!"register".equalsIgnoreCase(type)) {
                return;
            }

            String clientId = payload.has("clientId") ? payload.get("clientId").getAsString() : "anonymous";
            String launcherVersion = payload.has("launcher") ? payload.get("launcher").getAsString() : "unknown";
            String appVersion = payload.has("app") ? payload.get("app").getAsString() : "unknown";
            String contact = payload.has("contact") ? payload.get("contact").getAsString() : conn.getRemoteSocketAddress().toString();

            notificationHub.registerClient(clientId, launcherVersion, appVersion, contact, conn);

            JsonObject ack = new JsonObject();
            ack.addProperty("type", "registered");
            ack.addProperty("clientId", clientId);
            conn.send(gson.toJson(ack));
        } catch (Exception e) {
            System.err.println("WebSocket register parse error: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            notificationHub.unregisterSocket(conn);
        }
        System.err.println("WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket notification server started on port " + getPort());
    }
}

