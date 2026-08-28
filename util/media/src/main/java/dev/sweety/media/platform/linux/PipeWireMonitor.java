package dev.sweety.media.platform.linux;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sweety.media.AudioChannelMonitor;
import dev.sweety.media.data.AudioStream;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the output streams of PipeWire (or PulseAudio) through {@code pactl}.
 *
 * <p>{@code pactl subscribe} streams mixer events, so a track change or a pause
 * is picked up immediately instead of being polled for; a slow safety refresh
 * covers the case where the subscription dies.
 */
public final class PipeWireMonitor implements AudioChannelMonitor {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(PipeWireMonitor.class);

    private static final long REFRESH_DEBOUNCE_MS = 150;
    private static final long SAFETY_REFRESH_MS = 2_000;

    private final AtomicReference<List<AudioStream>> streams = new AtomicReference<>(List.of());
    private final AtomicBoolean running = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = ThreadUtil.singleThreadScheduler("luce-pipewire-monitor");
    private final ExecutorService subscriberExecutor = ThreadUtil.cachedThreadPool("luce-pipewire-subscribe");

    private Process subscribeProcess;
    private volatile long lastRefreshMs;

    public static boolean isAvailable() {
        return which("pactl");
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        refresh();
        scheduler.scheduleWithFixedDelay(this::refresh, SAFETY_REFRESH_MS, SAFETY_REFRESH_MS,
                TimeUnit.MILLISECONDS);
        subscriberExecutor.submit(this::runSubscriber);
    }

    @Override
    public List<AudioStream> streams() {
        return streams.get();
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
        subscriberExecutor.shutdownNow();
        if (subscribeProcess != null) {
            subscribeProcess.destroy();
        }
    }

    private void runSubscriber() {
        while (running.get()) {
            try {
                ProcessBuilder builder = new ProcessBuilder("pactl", "subscribe");
                builder.redirectErrorStream(true);
                subscribeProcess = builder.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(subscribeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running.get() && (line = reader.readLine()) != null) {
                        if (line.contains("sink-input") || line.contains("client")) {
                            refreshDebounced();
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("pactl subscribe failed: " + e);
            }
            if (running.get()) {
                sleep(1_000);
            }
        }
    }

    private void refreshDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_DEBOUNCE_MS) {
            return;
        }
        refresh();
    }

    private void refresh() {
        lastRefreshMs = System.currentTimeMillis();
        try {
            String json = runCommand("pactl", "-f", "json", "list", "sink-inputs");
            if (json == null || json.isBlank()) {
                streams.set(List.of());
                return;
            }
            streams.set(parse(json));
        } catch (Exception e) {
            LOG.debug("pactl list sink-inputs failed: " + e);
        }
    }

    public static List<AudioStream> parse(String json) {
        List<AudioStream> result = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return result;
        }
        JsonArray array = root.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject sinkInput = element.getAsJsonObject();
            JsonObject props = sinkInput.has("properties") && sinkInput.get("properties").isJsonObject()
                    ? sinkInput.getAsJsonObject("properties")
                    : new JsonObject();

            String mediaClass = str(props, "media.class");
            if (mediaClass != null && !mediaClass.contains("Output")) {
                continue;
            }

            result.add(new AudioStream(
                    intOf(sinkInput, "index", -1),
                    longOf(str(props, "application.process.id")),
                    firstNonBlank(str(props, "application.name"), str(props, "node.name")),
                    firstNonBlank(str(props, "application.process.binary"), str(props, "node.name")),
                    boolOf(sinkInput, "corked") || "true".equals(str(props, "pulse.corked")),
                    boolOf(sinkInput, "mute"),
                    volumePercent(sinkInput)));
        }
        return result;
    }

    /** Highest per-channel volume of the stream, as a 0..100 value. */
    private static int volumePercent(JsonObject sinkInput) {
        if (!sinkInput.has("volume") || !sinkInput.get("volume").isJsonObject()) {
            return 100;
        }
        int max = 0;
        for (String channel : sinkInput.getAsJsonObject("volume").keySet()) {
            JsonElement entry = sinkInput.getAsJsonObject("volume").get(channel);
            if (!entry.isJsonObject()) {
                continue;
            }
            String percent = str(entry.getAsJsonObject(), "value_percent");
            if (percent == null) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(percent.replace("%", "").trim()));
            } catch (NumberFormatException ignored) {
                // leave max as is
            }
        }
        return max;
    }

    private static String str(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int intOf(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static boolean boolOf(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static long longOf(String value) {
        try {
            return value == null ? -1 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private static String runCommand(String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Process process = builder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (a, b) -> a + b);
        }
        process.waitFor(3, TimeUnit.SECONDS);
        return output;
    }

    private static boolean which(String binary) {
        try {
            return new ProcessBuilder("which", binary).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
