package dev.sweety.media.platform.linux;

import dev.sweety.media.ArtworkResolver;
import dev.sweety.media.MediaSource;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;
import dev.sweety.thread.ThreadUtil;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every MPRIS player on the session bus: Spotify, any browser playing YouTube
 * Music / Tidal / Deezer, VLC, mpv, Rhythmbox — anything that implements the
 * spec. No per-application integration, no API keys.
 *
 * <p>Metadata is refreshed on a short interval rather than purely on signals,
 * because {@code Position} is deliberately signal-less in the MPRIS spec and the
 * progress bar needs it.
 */
public final class MprisSource implements MediaSource {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(MprisSource.class);

    private static final String BUS_PREFIX = "org.mpris.MediaPlayer2.";
    private static final String PLAYER_IFACE = "org.mpris.MediaPlayer2.Player";
    private static final String ROOT_IFACE = "org.mpris.MediaPlayer2";
    private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
    private static final long POLL_INTERVAL_MS = 400;

    private final AtomicReference<List<PlayerSnapshot>> snapshots = new AtomicReference<>(List.of());
    private final AtomicBoolean running = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = ThreadUtil.singleThreadScheduler("luce-mpris-source");

    private DBusConnection connection;
    private DBus bus;

    public static boolean isAvailable() {
        String address = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        return address != null && !address.isBlank()
                || System.getenv("XDG_RUNTIME_DIR") != null;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            connection = DBusConnectionBuilder.forSessionBus().build();
            bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        } catch (Exception e) {
            running.set(false);
            throw new IllegalStateException("cannot connect to the D-Bus session bus", e);
        }
        refresh();
        scheduler.scheduleWithFixedDelay(this::refresh, POLL_INTERVAL_MS, POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public List<PlayerSnapshot> players() {
        return snapshots.get();
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdownNow();
        if (connection != null) {
            connection.disconnect();
        }
    }

    private void refresh() {
        try {
            List<PlayerSnapshot> found = new ArrayList<>();
            for (String busName : playerBusNames()) {
                PlayerSnapshot snapshot = read(busName);
                if (snapshot != null) {
                    found.add(snapshot);
                }
            }
            found.sort(Comparator.comparing(PlayerSnapshot::id));
            snapshots.set(List.copyOf(found));
        } catch (Exception e) {
            LOG.debug("MPRIS refresh failed: " + e);
        }
    }

    private List<String> playerBusNames() {
        List<String> names = new ArrayList<>();
        for (String name : bus.ListNames()) {
            if (name.startsWith(BUS_PREFIX)) {
                names.add(name);
            }
        }
        return names;
    }

    private PlayerSnapshot read(String busName) {
        try {
            Properties props = connection.getRemoteObject(busName, OBJECT_PATH, Properties.class);
            Map<String, Variant<?>> playerProps = props.GetAll(PLAYER_IFACE);
            if (playerProps == null || playerProps.isEmpty()) {
                return null;
            }

            String playbackStatus = string(playerProps.get("PlaybackStatus"), "Stopped");
            Map<String, Variant<?>> metadata = metadata(playerProps.get("Metadata"));

            String title = string(metadata.get("xesam:title"), "");
            String artist = joinStrings(metadata.get("xesam:artist"));
            String album = string(metadata.get("xesam:album"), "");
            String artUrl = string(metadata.get("mpris:artUrl"), "");
            String trackId = trackId(metadata, busName, title, artist);
            double duration = micros(metadata.get("mpris:length"));
            double position = micros(playerProps.get("Position"));
            boolean shuffle = bool(playerProps.get("Shuffle"));
            String loop = string(playerProps.get("LoopStatus"), "None");
            int volume = (int) Math.round(doubleOf(playerProps.get("Volume"), 1.0) * 100);

            MediaStatus status = new MediaStatus(
                    title,
                    artist,
                    album,
                    position,
                    duration,
                    ArtworkResolver.resolve(artUrl),
                    trackId,
                    "Playing".equals(playbackStatus),
                    shuffle,
                    Math.clamp(volume, 0, 100),
                    loop.toLowerCase(Locale.ROOT),
                    identity(busName, props));

            return new PlayerSnapshot(busName, pidOf(busName), identity(busName, props),
                    binaryOf(busName), status);
        } catch (Exception e) {
            LOG.debug("cannot read MPRIS player " + busName + ": " + e);
            return null;
        }
    }

    /** Friendly name from the root interface, falling back to the bus-name suffix. */
    private String identity(String busName, Properties props) {
        try {
            Object identity = props.Get(ROOT_IFACE, "Identity");
            if (identity != null && !identity.toString().isBlank()) {
                return identity.toString();
            }
        } catch (Exception ignored) {
            // players are allowed to omit it
        }
        return shortName(busName);
    }

    private String binaryOf(String busName) {
        try {
            Properties props = connection.getRemoteObject(busName, OBJECT_PATH, Properties.class);
            Object entry = props.Get(ROOT_IFACE, "DesktopEntry");
            if (entry != null && !entry.toString().isBlank()) {
                return entry.toString();
            }
        } catch (Exception ignored) {
            // optional property
        }
        return shortName(busName);
    }

    /** MPRIS bus names look like org.mpris.MediaPlayer2.brave.instance9771. */
    private static String shortName(String busName) {
        String tail = busName.substring(BUS_PREFIX.length());
        int dot = tail.indexOf('.');
        return dot > 0 ? tail.substring(0, dot) : tail;
    }

    private long pidOf(String busName) {
        try {
            return bus.GetConnectionUnixProcessID(busName).longValue();
        } catch (Exception e) {
            return -1;
        }
    }

    // --- controls -------------------------------------------------------

    private MprisPlayerInterface player(String busName) {
        try {
            return connection.getRemoteObject(busName, OBJECT_PATH, MprisPlayerInterface.class);
        } catch (Exception e) {
            LOG.debug("cannot bind player " + busName + ": " + e);
            return null;
        }
    }

    @Override
    public void playPause(String playerId) {
        MprisPlayerInterface player = player(playerId);
        if (player != null) {
            player.PlayPause();
        }
    }

    @Override
    public void next(String playerId) {
        MprisPlayerInterface player = player(playerId);
        if (player != null) {
            player.Next();
        }
    }

    @Override
    public void previous(String playerId) {
        MprisPlayerInterface player = player(playerId);
        if (player != null) {
            player.Previous();
        }
    }

    @Override
    public void seek(String playerId, double seconds) {
        try {
            Properties props = connection.getRemoteObject(playerId, OBJECT_PATH, Properties.class);
            Map<String, Variant<?>> metadata = metadata(props.GetAll(PLAYER_IFACE).get("Metadata"));
            Object rawTrackId = metadata.containsKey("mpris:trackid")
                    ? metadata.get("mpris:trackid").getValue()
                    : null;
            MprisPlayerInterface player = player(playerId);
            if (player == null) {
                return;
            }
            long micros = (long) (seconds * 1_000_000L);
            if (rawTrackId instanceof DBusPath path) {
                player.SetPosition(path, micros);
            } else if (rawTrackId != null) {
                player.SetPosition(new DBusPath(rawTrackId.toString()), micros);
            } else {
                // No track id: fall back to a relative seek from the current position.
                double current = micros(props.GetAll(PLAYER_IFACE).get("Position"));
                player.Seek(micros - (long) (current * 1_000_000L));
            }
        } catch (Exception e) {
            LOG.debug("seek failed on " + playerId + ": " + e);
        }
    }

    @Override
    public void setVolume(String playerId, int percent) {
        try {
            Properties props = connection.getRemoteObject(playerId, OBJECT_PATH, Properties.class);
            props.Set(PLAYER_IFACE, "Volume", Math.clamp(percent, 0, 100) / 100.0);
        } catch (Exception e) {
            LOG.debug("volume change failed on " + playerId + ": " + e);
        }
    }

    // --- variant helpers ------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Variant<?>> metadata(Variant<?> variant) {
        if (variant == null || !(variant.getValue() instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return (Map<String, Variant<?>>) map;
    }

    private static String string(Variant<?> variant, String fallback) {
        if (variant == null || variant.getValue() == null) {
            return fallback;
        }
        return variant.getValue().toString();
    }

    /** xesam:artist is a string array; join it the way players display it. */
    private static String joinStrings(Variant<?> variant) {
        if (variant == null || variant.getValue() == null) {
            return "";
        }
        Object value = variant.getValue();
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    parts.add(item.toString());
                }
            }
            return String.join(", ", parts);
        }
        if (value instanceof Object[] array) {
            List<String> parts = new ArrayList<>();
            for (Object item : array) {
                if (item != null && !item.toString().isBlank()) {
                    parts.add(item.toString());
                }
            }
            return String.join(", ", parts);
        }
        return value.toString();
    }

    private static double micros(Variant<?> variant) {
        if (variant == null || !(variant.getValue() instanceof Number number)) {
            return 0;
        }
        return number.doubleValue() / 1_000_000.0;
    }

    private static double doubleOf(Variant<?> variant, double fallback) {
        if (variant == null || !(variant.getValue() instanceof Number number)) {
            return fallback;
        }
        return number.doubleValue();
    }

    private static boolean bool(Variant<?> variant) {
        return variant != null && Boolean.TRUE.equals(variant.getValue());
    }

    /** Prefer the player's own track id; otherwise derive a stable one. */
    private static String trackId(Map<String, Variant<?>> metadata, String busName,
                                   String title, String artist) {
        Variant<?> raw = metadata.get("mpris:trackid");
        if (raw != null && raw.getValue() != null && !raw.getValue().toString().isBlank()) {
            return raw.getValue().toString();
        }
        return busName + "#" + artist + "#" + title;
    }
}
