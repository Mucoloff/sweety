package dev.sweety.launcher.update;

import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.versioning.protocol.handshake.DownloadType;
import dev.sweety.versioning.protocol.handshake.State;
import dev.sweety.versioning.version.artifact.Artifact;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class UpdateManager {

    private final EnumMap<Artifact, Path> artifactPathMap;

    private final Consumer<State> handshakeState;

    private final AtomicReference<LauncherConfig> config;

    public UpdateManager(AtomicReference<LauncherConfig> config, EnumMap<Artifact, Path> artifactPathMap, Consumer<State> handshakeState) {
        this.config = config;
        this.artifactPathMap = artifactPathMap;
        this.handshakeState = handshakeState;
    }

    public void downloadUpdate(Artifact artifact, String token, DownloadType type) {
        try {
            //todo patch
            downloadArtifact(token, artifactPathMap.get(artifact));
            complete(State.UPDATED);
        } catch (Exception e) {
            complete(State.UNAVAILABLE);
            e.printStackTrace(System.err);
        }
    }

    public void upToDate() {
        complete(State.UP_TO_DATE);
    }

    public void unavailable() {
        complete(State.UNAVAILABLE);
    }

    private void complete(State state) {
        if (handshakeState != null) handshakeState.accept(state);
    }

    private void downloadArtifact(String token, Path destination) throws Exception {
        final String serverUrl = config.get().url();
        final UUID clientId = config.get().clientId();
        final String qToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        final String qClient = URLEncoder.encode(clientId.toString(), StandardCharsets.UTF_8);
        final URL downloadUrl = new URI(serverUrl + "/download?clientId=" + qClient + "&token=" + qToken).toURL();

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
