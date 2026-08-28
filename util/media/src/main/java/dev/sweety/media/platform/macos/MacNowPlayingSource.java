package dev.sweety.media.platform.macos;

import dev.sweety.media.MediaSource;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS now-playing information through {@code nowplaying-cli}.
 *
 * <p>Support here is deliberately partial: Apple locked down the private
 * MediaRemote framework in macOS 15.4, so there is no supported in-process API
 * left. When {@code nowplaying-cli} (brew install nowplaying-cli) is missing,
 * the overlay simply stays hidden on this platform instead of guessing.
 */
public final class MacNowPlayingSource implements MediaSource {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(MacNowPlayingSource.class);

    private static final long POLL_INTERVAL_MS = 700;
    // Per-track file, not one fixed shared path: AlbumArtCache keys its cache by this exact path
    // string, so every track sharing one fixed filename meant the cache saw the same "source" forever
    // and never refetched — the cover stayed stuck on whatever track loaded first.
    private static final Path ARTWORK_DIR = Path.of(System.getProperty("java.io.tmpdir"));

    /**
     * {@code nowplaying-cli get elapsedTime} always answers 0 — a bug in that
     * subcommand, not in the underlying MediaRemote data. {@code get-raw} carries
     * the real, live value under this key, so elapsed time is scraped from there
     * instead.
     */
    private static final Pattern ELAPSED_TIME_PATTERN =
            Pattern.compile("\"kMRMediaRemoteNowPlayingInfoElapsedTime\"\\s*:\\s*([0-9.]+)");

    private final AtomicReference<List<PlayerSnapshot>> players = new AtomicReference<>(List.of());
    private final AtomicBoolean running = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = ThreadUtil.singleThreadScheduler("luce-macos-nowplaying");

    private String lastArtworkKey = "";

    /**
     * MediaRemote's elapsedTime is a checkpoint updated only on play/pause/seek/
     * track-change events, not a continuously ticking clock — it holds the same
     * value for seconds at a time between events. There is no accompanying
     * timestamp field, so the position shown between checkpoints is extrapolated
     * from wall-clock time here, the same way {@link dev.sweety.media.platform.DemoSource}
     * fakes a playing track.
     */
    private double checkpointElapsed = -1;
    private long checkpointAtMillis;

    public static boolean isAvailable() {
        return run("which", "nowplaying-cli") != null;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!isAvailable()) {
            LOG.warn("nowplaying-cli not found; macOS media info is unavailable "
                    + "(install it with: brew install nowplaying-cli)");
            return;
        }
        refresh();
        scheduler.scheduleWithFixedDelay(this::refresh, POLL_INTERVAL_MS, POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public List<PlayerSnapshot> players() {
        return players.get();
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
    }

    private void refresh() {
        // One call, one field per line, in this exact order.
        String output = run("nowplaying-cli", "get", "title", "artist", "album",
                "duration", "elapsedTime", "playbackRate", "bundleIdentifier");
        if (output == null) {
            players.set(List.of());
            return;
        }
        String[] lines = output.split("\n", -1);
        String title = field(lines, 0);
        String artist = field(lines, 1);
        String album = field(lines, 2);
        double duration = number(field(lines, 3));
        boolean playing = number(field(lines, 5)) > 0;
        double elapsed = interpolatedElapsedTime(playing);
        String bundle = field(lines, 6);

        if (title.isBlank() && artist.isBlank()) {
            players.set(List.of());
            return;
        }

        String trackId = bundle + "#" + artist + "#" + title;
        String artwork = extractArtwork(trackId);

        MediaStatus status = new MediaStatus(title, artist, album, elapsed, duration, artwork,
                trackId, playing, false, 100, "none", bundle);
        players.set(List.of(new PlayerSnapshot(bundle.isBlank() ? "nowplaying" : bundle,
                -1, bundle, bundle, status)));
    }

    private double interpolatedElapsedTime(boolean playing) {
        double raw = rawElapsedTime();
        long now = System.currentTimeMillis();
        if (raw != checkpointElapsed) {
            checkpointElapsed = raw;
            checkpointAtMillis = now;
        }
        if (checkpointElapsed < 0) {
            return 0;
        }
        return playing ? checkpointElapsed + (now - checkpointAtMillis) / 1000.0 : checkpointElapsed;
    }

    private static double rawElapsedTime() {
        String raw = run("nowplaying-cli", "get-raw");
        if (raw == null) {
            return -1;
        }
        Matcher matcher = ELAPSED_TIME_PATTERN.matcher(raw);
        return matcher.find() ? number(matcher.group(1)) : -1;
    }

    /** Artwork comes back base64-encoded; write it out once per track, to a per-track file. */
    private String extractArtwork(String trackId) {
        try {
            Path artworkPath = ARTWORK_DIR.resolve("luce-media-artwork-"
                    + Integer.toHexString(trackId.hashCode()) + ".png");
            if (trackId.equals(lastArtworkKey) && Files.exists(artworkPath)) {
                return artworkPath.toString();
            }
            String base64 = run("nowplaying-cli", "get", "artworkData");
            if (base64 == null || base64.isBlank() || "null".equals(base64.trim())) {
                return null;
            }
            base64 = base64.trim();
            // Some builds emit a "data:image/png;base64,<payload>" URI instead of bare base64 —
            // Base64.getMimeDecoder() silently skips the non-alphabet chars in the prefix rather than
            // throwing, which desynchronizes the byte stream instead of failing loudly, producing a
            // corrupt image (observed as a "Bad PNG Signature" decode error downstream).
            int comma = base64.indexOf(',');
            if (base64.startsWith("data:") && comma >= 0) {
                base64 = base64.substring(comma + 1);
            }
            byte[] bytes = Base64.getMimeDecoder().decode(base64);
            boolean looksLikePng = bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            LOG.info("[nowplaying] artwork decoded " + bytes.length + " bytes, pngHeader=" + looksLikePng);
            try (var out = Files.newOutputStream(artworkPath, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                out.write(bytes);
            }
            lastArtworkKey = trackId;
            return artworkPath.toString();
        } catch (Exception e) {
            LOG.warn("artwork extraction failed: " + e);
            return null;
        }
    }

    @Override
    public void playPause(String playerId) {
        run("nowplaying-cli", "togglePlayPause");
    }

    @Override
    public void next(String playerId) {
        run("nowplaying-cli", "next");
    }

    @Override
    public void previous(String playerId) {
        run("nowplaying-cli", "previous");
    }

    @Override
    public void seek(String playerId, double seconds) {
        run("nowplaying-cli", "seek", String.valueOf((long) seconds));
    }

    @Override
    public void setVolume(String playerId, int percent) {
        run("osascript", "-e", "set volume output volume " + Math.clamp(percent, 0, 100));
    }

    private static String field(String[] lines, int index) {
        if (index >= lines.length) {
            return "";
        }
        String value = lines[index].trim();
        return "null".equals(value) ? "" : value;
    }

    private static double number(String value) {
        try {
            return value.isBlank() ? 0 : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = String.join("\n", reader.lines().toList());
            }
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0 ? output : null;
        } catch (Exception e) {
            return null;
        }
    }
}
