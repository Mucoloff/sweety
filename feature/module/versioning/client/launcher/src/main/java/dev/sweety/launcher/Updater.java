package dev.sweety.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.sweety.launcher.config.LauncherConfig;
import dev.sweety.versioning.version.Artifact;
import dev.sweety.versioning.version.ReleaseInfo;
import dev.sweety.versioning.version.Version;
import dev.sweety.versioning.version.channel.Channel;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;

public class Updater {

    private static final Gson GSON = new Gson().newBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public static ReleaseInfo fetchLatest(String serverUrl) throws Exception {
        URL latestUrl = URI.create(serverUrl + "/latest").toURL();
        HttpURLConnection conn = (HttpURLConnection) latestUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(8_000);

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("latest failed status=" + status);
        }

        try (InputStream in = conn.getInputStream()) {
            JsonObject root = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            String launcherVersion = root.get("launcher").getAsString();
            String appVersion = root.get("app").getAsString();
            return new ReleaseInfo(Version.ZERO, Channel.STABLE, Instant.MIN);
        }
    }

    public static boolean needsUpdate(Artifact artifact, LauncherConfig config, ReleaseInfo latest) {
        Version current = config.versions().get(artifact);
        return latest.version().newerThan(current);
    }

    public static void updateApp(String serverUrl, String clientId, String version, Path appJar) throws Exception {
        downloadArtifact(serverUrl, "app", clientId, version, appJar);
    }

    public static boolean updateLauncherSelf(String serverUrl, String clientId, String version, Path selfJar) throws Exception {
        Path currentJar = detectCurrentJar();
        if (currentJar == null) {
            System.err.println("Self-update skipped: launcher non avviato da jar.");
            return false;
        }

        downloadArtifact(serverUrl, "launcher", clientId, version, selfJar);

        /*
        if (isWindows()) {
            launchHelperWindows(selfJar, updateJar);
        } else {
            // Linux, macOS, Solaris, Unknown → bash
            launchHelperUnix(selfJar, updateJar);
        }
         */
        return true;
    }

    /**
     * Helper per Linux / macOS: script bash che aspetta la JVM, sostituisce il jar e riavvia.
     */
    private static void launchHelperUnix(Path selfJar, Path updateJar) throws Exception {
        String src = escapeSingleQuotes(updateJar.toAbsolutePath().toString());
        String dest = escapeSingleQuotes(selfJar.toAbsolutePath().toString());

        String script = "#!/usr/bin/env bash\n"
                + "set -e\n"
                + "sleep 1\n"
                + "mv -f '" + src + "' '" + dest + "'\n"
                + "exec java -jar '" + dest + "'\n";

        Path helperScript = selfJar.resolveSibling("launcher-self-update.sh");
        Files.writeString(helperScript, script, StandardCharsets.UTF_8);
        if (!helperScript.toFile().setExecutable(true)) {
            throw new IllegalStateException("Impossibile rendere eseguibile: " + helperScript);
        }

        new ProcessBuilder("bash", helperScript.toAbsolutePath().toString())
                .inheritIO()
                .start();
    }

    /**
     * Helper per Windows: batch file che aspetta il pid corrente, copia il jar e riavvia.
     * Usa {@code ping} come sostituto di {@code sleep} (disponibile su tutti i Windows).
     */
    private static void launchHelperWindows(Path selfJar, Path updateJar) throws Exception {
        String src = updateJar.toAbsolutePath().toString();
        String dest = selfJar.toAbsolutePath().toString();

        // Su Windows non esiste mv atomico cross-drive; usiamo move /Y
        String script = "@echo off\r\n"
                + "ping -n 2 127.0.0.1 > nul\r\n"          // ~1 secondo di attesa
                + "move /Y \"" + src + "\" \"" + dest + "\"\r\n"
                + "start \"\" javaw -jar \"" + dest + "\"\r\n"
                + "exit\r\n";

        Path helperScript = selfJar.resolveSibling("launcher-self-update.bat");
        Files.writeString(helperScript, script, StandardCharsets.UTF_8);

        new ProcessBuilder("cmd.exe", "/C", "start", "", helperScript.toAbsolutePath().toString())
                .inheritIO()
                .start();
    }

    private static void downloadArtifact(String serverUrl,
                                         String artifact,
                                         String clientId,
                                         String version,
                                         Path destination) throws Exception {
        String qClient = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        String qVersion = URLEncoder.encode(version, StandardCharsets.UTF_8);
        URL downloadUrl = new URI(serverUrl + "/download?artifact=" + artifact + "&clientId=" + qClient + "&version=" + qVersion).toURL();

        Exception last = new IllegalStateException("download failed without details");
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path tmp = destination.resolveSibling(destination.getFileName() + ".part");
            try {
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(20_000);

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("download failed status=" + status);

                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(300L * attempt);
            }
        }
        throw last;
    }

    private static Path detectCurrentJar() {
        try {
            Path path = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".jar")) {
                return null;
            }
            return path;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String escapeSingleQuotes(String value) {
        return value.replace("'", "'\\''");
    }
}