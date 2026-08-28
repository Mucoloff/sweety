package dev.sweety.media.platform;

import dev.sweety.media.AudioChannelMonitor;
import dev.sweety.media.MediaSource;
import dev.sweety.media.data.AudioStream;
import dev.sweety.media.data.PlayerSnapshot;
import dev.sweety.media.platform.linux.MprisSource;
import dev.sweety.media.platform.linux.PipeWireMonitor;
import dev.sweety.media.platform.macos.CoreAudioMonitor;
import dev.sweety.media.platform.macos.MacNowPlayingSource;
import dev.sweety.media.platform.windows.SmtcSource;
import dev.sweety.media.platform.windows.WasapiMonitor;
import dev.sweety.util.logger.LoggerFactory;
import dev.sweety.util.logger.SimpleLogger;

import java.util.List;
import java.util.Locale;

/** Chooses the media source and mixer monitor for the running OS. */
public final class Platform {

    private static final SimpleLogger LOG = LoggerFactory.getLogger(Platform.class);

    public enum Os { LINUX, WINDOWS, MACOS, UNKNOWN }

    private static final Os OS = detect();

    private Platform() {
    }

    public static Os os() {
        return OS;
    }

    private static Os detect() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("linux") || name.contains("bsd")) {
            return Os.LINUX;
        }
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.MACOS;
        }
        return Os.UNKNOWN;
    }

    public static MediaSource createMediaSource() {
        return switch (OS) {
            case LINUX -> new MprisSource();
            case WINDOWS -> new SmtcSource();
            case MACOS -> new MacNowPlayingSource();
            case UNKNOWN -> throw new IllegalStateException(
                    "unsupported platform: " + System.getProperty("os.name"));
        };
    }

    public static AudioChannelMonitor createAudioMonitor() {
        return switch (OS) {
            case LINUX -> {
                if (!PipeWireMonitor.isAvailable()) {
                    LOG.warn("pactl not found: falling back to artwork-only track selection");
                    yield new AlwaysActiveMonitor();
                }
                yield new PipeWireMonitor();
            }
            // On Windows and macOS the bridge reports session state together with
            // the metadata, so the monitor reads from the same source.
            case WINDOWS -> new WasapiMonitor();
            case MACOS -> new CoreAudioMonitor();
            case UNKNOWN -> new AlwaysActiveMonitor();
        };
    }

    /** Degrades gracefully: every player is treated as if it owned a live stream. */
    public static final class AlwaysActiveMonitor implements AudioChannelMonitor {
        @Override
        public void start() {
        }

        @Override
        public List<AudioStream> streams() {
            return List.of();
        }

        @Override
        public boolean isReliable() {
            return false;
        }

        @Override
        public void close() {
        }
    }

    /** Convenience for the debug CLI. */
    public static String describe(PlayerSnapshot player) {
        return player.appName() + " [" + player.id() + " pid=" + player.pid() + "]";
    }
}
