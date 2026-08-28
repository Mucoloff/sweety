package dev.sweety.media.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sweety.math.list.ConcurrentHashSet;
import dev.sweety.media.data.Lyrics;
import dev.sweety.media.data.LyricsLine;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up synced lyrics on <a href="https://lrclib.net">LRCLIB</a> — free,
 * no API key, keyed by artist/title/album/duration. Results are cached per
 * track so switching back to a recently seen song is instant.
 */
public final class LyricsClient implements AutoCloseable {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(LyricsClient.class);

    private static final Pattern LRC_LINE =
            Pattern.compile("^\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)$");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ConcurrentHashMap<String, Lyrics> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashSet.create();
    private final ExecutorService executor = ThreadUtil.fixedThreadPool(1, "luce-lyrics-fetch");

    /** @return the cached lyrics, or null while the lookup is still in flight */
    public Lyrics get(MediaStatus status) {
        if (status.artist().isBlank() || status.title().isBlank()) {
            return Lyrics.NONE;
        }
        String key = status.trackId();
        Lyrics cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (inFlight.add(key)) {
            executor.submit(() -> fetch(key, status));
        }
        return null;
    }

    private void fetch(String key, MediaStatus status) {
        try {
            Lyrics found = getExact(status);
            if (found == null || found.isEmpty()) {
                Lyrics searched = search(status);
                if (searched != null) {
                    found = searched;
                }
            }
            cache.put(key, found != null ? found : Lyrics.NONE);
        } catch (Exception e) {
            LOG.debug("lyrics lookup failed for " + status.artist() + " - " + status.title() + ": " + e);
            cache.put(key, Lyrics.NONE);
        } finally {
            inFlight.remove(key);
        }
    }

    /** Exact match on artist/title/album/duration — LRCLIB's strict, fast path. */
    private Lyrics getExact(MediaStatus status) throws Exception {
        String url = "https://lrclib.net/api/get?artist_name=" + encode(status.artist())
                + "&track_name=" + encode(status.title())
                + "&album_name=" + encode(status.album())
                + "&duration=" + Math.round(status.durationSeconds());
        HttpResponse<String> response = send(url);
        return response.statusCode() == 200
                ? parse(JsonParser.parseString(response.body()).getAsJsonObject()) : null;
    }

    /**
     * Fuzzy fallback: the exact match above fails on almost any "(feat. X)",
     * "- Remix" or similar suffix a streaming service tacks onto the title, so
     * this searches instead and takes the closest hit.
     */
    private Lyrics search(MediaStatus status) throws Exception {
        String url = "https://lrclib.net/api/search?track_name=" + encode(status.title())
                + "&artist_name=" + encode(status.artist());
        HttpResponse<String> response = send(url);
        if (response.statusCode() != 200) {
            return null;
        }
        JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();
        JsonObject bestWithSync = null;
        JsonObject best = null;
        for (var element : results) {
            JsonObject candidate = element.getAsJsonObject();
            if (best == null) {
                best = candidate;
            }
            if (bestWithSync == null && stringOrNull(candidate, "syncedLyrics") != null) {
                bestWithSync = candidate;
                break;
            }
        }
        JsonObject chosen = bestWithSync != null ? bestWithSync : best;
        return chosen != null ? parse(chosen) : null;
    }

    private HttpResponse<String> send(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Lyrics parse(JsonObject json) {
        if (json == null) {
            return null;
        }
        String synced = stringOrNull(json, "syncedLyrics");
        String plain = stringOrNull(json, "plainLyrics");
        return new Lyrics(synced != null ? parseLrc(synced) : List.of(), plain);
    }

    private static String stringOrNull(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull() ? json.get(field).getAsString() : null;
    }

    private static List<LyricsLine> parseLrc(String lrc) {
        List<LyricsLine> lines = new ArrayList<>();
        DoubleList timestamps = new DoubleArrayList();
        List<String> texts = new ArrayList<>();
        for (String rawLine : lrc.split("\n")) {
            Matcher matcher = LRC_LINE.matcher(rawLine.trim());
            if (!matcher.matches()) {
                continue;
            }
            double minutes = Double.parseDouble(matcher.group(1));
            double seconds = Double.parseDouble(matcher.group(2));
            String text = matcher.group(3).trim();
            if (!text.isEmpty()) {
                timestamps.add(minutes * 60 + seconds);
                texts.add(text);
            }
        }
        for (int i = 0; i < timestamps.size(); i++) {
            lines.add(new LyricsLine(timestamps.getDouble(i), texts.get(i)));
        }
        lines.sort((a, b) -> Double.compare(a.timeSeconds(), b.timeSeconds()));
        return lines;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
