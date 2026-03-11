package dev.sweety.versioning.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;

import java.util.concurrent.ConcurrentHashMap;

public class NotificationHub {

    public record ClientSession(String clientId, String launcherVersion, String appVersion, String contact, WebSocket socket) {
    }

    private final ConcurrentHashMap<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WebSocket, String> socketOwners = new ConcurrentHashMap<>();
    private final Gson gson = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public void registerClient(String clientId, String launcherVersion, String appVersion, String contact, WebSocket socket) {
        ClientSession session = new ClientSession(clientId, launcherVersion, appVersion, contact, socket);
        activeSessions.put(clientId, session);
        socketOwners.put(socket, clientId);
        System.out.println("Client registered via WebSocket: " + clientId + " launcher=" + launcherVersion + " app=" + appVersion);
    }

    public void unregisterClient(String clientId) {
        ClientSession removed = activeSessions.remove(clientId);
        if (removed != null) {
            socketOwners.remove(removed.socket());
            System.out.println("Client unregistered: " + clientId);
        }
    }

    public void unregisterSocket(WebSocket socket) {
        String clientId = socketOwners.remove(socket);
        if (clientId != null) {
            activeSessions.remove(clientId);
            System.out.println("Client socket unregistered: " + clientId);
        }
    }

    public void broadcastRelease(String baseUrl, String launcherVersion, String appVersion) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "release");
        payload.addProperty("launcher", launcherVersion);
        payload.addProperty("app", appVersion);
        payload.addProperty("downloadLauncher", baseUrl + "/download?artifact=launcher&version=" + launcherVersion);
        payload.addProperty("downloadApp", baseUrl + "/download?artifact=app&version=" + appVersion);

        String json = gson.toJson(payload);
        for (ClientSession session : activeSessions.values()) {
            try {
                session.socket().send(json);
                System.out.println("notify to " + session.clientId +" " + json);
            } catch (Exception e) {
                System.err.println("Failed to notify client " + session.clientId() + ": " + e.getMessage());
                unregisterClient(session.clientId());
            }
        }
    }

    public int getActiveClientCount() {
        return activeSessions.size();
    }
}
