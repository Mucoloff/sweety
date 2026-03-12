package dev.sweety.launcher.update;

import dev.sweety.versioning.protocol.handshake.State;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UpdateManager {

    private final String serverUrl;
    private final UUID clientId;
    private final Path appJar;
    private final Path selfJar;
    private final CompletableFuture<State> handshakeState;

    public UpdateManager(String serverUrl, UUID clientId, Path appJar, Path selfJar, CompletableFuture<State> handshakeState) {
        this.serverUrl = serverUrl;
        this.clientId = clientId;
        this.appJar = appJar;
        this.selfJar = selfJar;
        this.handshakeState = handshakeState;
    }

    public void downloadAppUpdate(String appToken) {
        try {
            downloadArtifact(appToken, appJar);
            complete(State.APP);
        } catch (Exception e) {
            complete(State.UNAVAILABLE);
            e.printStackTrace(System.err);
        }
    }

    public void downloadLauncherUpdate(String launcherToken) {
        try {
            downloadArtifact(launcherToken, selfJar);
            complete(State.LAUNCHER);
        } catch (Exception e) {
            complete(State.UNAVAILABLE);
        }
    }

    public void upToDate() {
        complete(State.UP_TO_DATE);
    }

    public void unavailable() {
        complete(State.UNAVAILABLE);
    }

    private void complete(State state) {
        if (handshakeState != null) handshakeState.complete(state);
    }

    private void downloadArtifact(String token, Path destination) throws Exception {
        String qToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String qClient = URLEncoder.encode(clientId.toString(), StandardCharsets.UTF_8);
        URL downloadUrl = new URI(serverUrl + "/download?clientId=" + qClient + "&token=" + qToken).toURL();

        Exception last = new IllegalStateException("download failed without details");
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path tmp = destination.resolveSibling(destination.getFileName() + ".part");
            try {
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(20_000);

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("download failed status=" + status);
                }

                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(1000L * attempt);
            }
        }
        throw last;
    }

}
