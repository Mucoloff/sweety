package dev.sweety.media.platform.windows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sweety.media.data.AudioStream;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Talks to the C# helper that exposes the Windows System Media Transport
 * Controls and the WASAPI audio sessions.
 *
 * <p>Protocol: one JSON object per line on stdout, commands as plain lines on
 * stdin. The reader also understands the extra fields an extended bridge may
 * report ({@code AppId}, {@code ProcessId}, {@code AudioActive},
 * {@code AudioVolumePercent}) and falls back to sane defaults without them.
 *
 * <p>The helper binary is built against the WinRT projections, which a plain
 * {@code csc} cannot compile without the Windows SDK, so it ships prebuilt; the
 * source travels with it so it can be rebuilt and replaced.
 */
public final class SmtcBridge {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(SmtcBridge.class);

    private static final SmtcBridge INSTANCE = new SmtcBridge();

    private static final String EXE_RESOURCE = "/assets/bridge/SMTCBridge.exe";
    private static final String SOURCE_RESOURCE = "/assets/bridge/SMTCBridge.cs";
    private static final Path CACHE_DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "luce-media-bridge");
    private static final Path SOURCE_PATH = CACHE_DIR.resolve("SMTCBridge.cs");
    private static final Path EXE_PATH = CACHE_DIR.resolve("SMTCBridge.exe");
    private static final Path ARTWORK_PATH = CACHE_DIR.resolve("artwork.png");

    private final AtomicReference<List<PlayerSnapshot>> players = new AtomicReference<>(List.of());
    private final AtomicReference<List<AudioStream>> streams = new AtomicReference<>(List.of());
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService ioExecutor = ThreadUtil.cachedThreadPool("luce-smtc-bridge-io");

    private Process process;
    private OutputStream stdin;

    private SmtcBridge() {
    }

    public static SmtcBridge getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            Path exe = ensureBridgeBinary();
            ProcessBuilder builder = new ProcessBuilder(exe.toString(), ARTWORK_PATH.toString());
            builder.redirectErrorStream(false);
            process = builder.start();
            stdin = process.getOutputStream();
            startReader(process.getInputStream());
            drainStderr(process.getErrorStream());
        } catch (Exception e) {
            running.set(false);
            LOG.error("cannot start the Windows media bridge", e);
        }
    }

    public void stop() {
        running.set(false);
        ioExecutor.shutdownNow();
        if (process != null) {
            process.destroy();
        }
    }

    public List<PlayerSnapshot> players() {
        return players.get();
    }

    public List<AudioStream> streams() {
        return streams.get();
    }

    public void send(String command) {
        if (stdin == null) {
            return;
        }
        try {
            stdin.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        } catch (Exception e) {
            LOG.debug("bridge command failed: " + e);
        }
    }

    /** Unpacks the helper (and its source, for reference) into a temp directory. */
    private Path ensureBridgeBinary() throws Exception {
        Files.createDirectories(CACHE_DIR);
        byte[] exe = resource(EXE_RESOURCE);
        if (!Files.exists(EXE_PATH) || !Arrays.equals(Files.readAllBytes(EXE_PATH), exe)) {
            Path temp = CACHE_DIR.resolve("SMTCBridge.new.exe");
            writeBytes(temp, exe);
            Files.move(temp, EXE_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            writeBytes(SOURCE_PATH, resource(SOURCE_RESOURCE));
        } catch (Exception e) {
            LOG.debug("cannot unpack the bridge source: " + e);
        }
        return EXE_PATH;
    }

    private static void writeBytes(Path path, byte[] bytes) throws Exception {
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            out.write(bytes);
        }
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = SmtcBridge.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is missing from the jar");
            }
            return in.readAllBytes();
        }
    }

    private void startReader(InputStream in) {
        ioExecutor.submit(() -> {
            try (BufferedReader buffered = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while (running.get() && (line = buffered.readLine()) != null) {
                    parseLine(line);
                }
            } catch (Exception e) {
                LOG.debug("bridge reader stopped: " + e);
            }
        });
    }

    private void drainStderr(InputStream in) {
        ioExecutor.submit(() -> {
            try (BufferedReader buffered = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while (running.get() && (line = buffered.readLine()) != null) {
                    LOG.debug("bridge: " + line);
                }
            } catch (Exception ignored) {
                // the process is going away
            }
        });
    }

    private void parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
            String appId = optString(json, "AppId", "smtc");
            long pid = json.has("ProcessId") ? json.get("ProcessId").getAsLong() : -1;
            String artwork = optString(json, "ArtworkPath", "");

            MediaStatus status = new MediaStatus(
                    optString(json, "Title", ""),
                    optString(json, "Artist", ""),
                    optString(json, "Album", ""),
                    optDouble(json, "PositionSeconds"),
                    optDouble(json, "DurationSeconds"),
                    artwork.isBlank() ? null : artwork,
                    optString(json, "TrackId", appId + "#" + optString(json, "Title", "")),
                    json.has("IsPlaying") && json.get("IsPlaying").getAsBoolean(),
                    json.has("Shuffle") && json.get("Shuffle").getAsBoolean(),
                    json.has("VolumePercent") ? json.get("VolumePercent").getAsInt() : 100,
                    optString(json, "RepeatState", "none"),
                    optString(json, "AppName", appId));

            players.set(List.of(new PlayerSnapshot(appId, pid,
                    optString(json, "AppName", appId), optString(json, "Binary", appId), status)));

            // The bridge reports whether that app currently owns an audible
            // WASAPI session; model it as a single mixer stream.
            boolean audioActive = !json.has("AudioActive") || json.get("AudioActive").getAsBoolean();
            int audioVolume = json.has("AudioVolumePercent")
                    ? json.get("AudioVolumePercent").getAsInt()
                    : 100;
            List<AudioStream> mixer = new ArrayList<>();
            if (audioActive) {
                mixer.add(new AudioStream(0, pid, optString(json, "AppName", appId),
                        optString(json, "Binary", appId), false, false, audioVolume));
            }
            streams.set(List.copyOf(mixer));
        } catch (Exception e) {
            LOG.debug("cannot parse bridge line: " + e);
        }
    }

    private static String optString(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static double optDouble(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : 0;
    }
}
