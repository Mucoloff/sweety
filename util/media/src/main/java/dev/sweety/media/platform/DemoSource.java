package dev.sweety.media.platform;

import dev.sweety.media.AudioChannelMonitor;
import dev.sweety.media.MediaSource;
import dev.sweety.media.data.AudioStream;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;

import java.util.List;

/**
 * A fake player for exercising the overlay without touching real playback.
 * Also doubles as its own mixer monitor.
 */
public final class DemoSource implements MediaSource, AudioChannelMonitor {

    private static final double DURATION = 213;

    private final String artworkPath;
    private long startedAt;
    private boolean playing = true;
    private double pausedAt;

    public DemoSource(String artworkPath) {
        this.artworkPath = artworkPath;
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
    }

    private double position() {
        if (!playing) {
            return pausedAt;
        }
        return ((System.currentTimeMillis() - startedAt) / 1000.0) % DURATION;
    }

    @Override
    public List<PlayerSnapshot> players() {
        MediaStatus status = new MediaStatus(
                "Midnight City", "M83", "Hurry Up, We're Dreaming",
                position(), DURATION, artworkPath, "demo-track", playing, false, 80,
                "none", "Demo");
        return List.of(new PlayerSnapshot("demo", ProcessHandle.current().pid(),
                "Demo Player", "luce-media", status));
    }

    @Override
    public List<AudioStream> streams() {
        return List.of(new AudioStream(0, ProcessHandle.current().pid(), "Demo Player",
                "luce-media", !playing, false, 80));
    }

    @Override
    public void playPause(String playerId) {
        if (playing) {
            pausedAt = position();
            playing = false;
        } else {
            startedAt = (long) (System.currentTimeMillis() - pausedAt * 1000);
            playing = true;
        }
    }

    @Override
    public void next(String playerId) {
        startedAt = System.currentTimeMillis();
    }

    @Override
    public void previous(String playerId) {
        startedAt = System.currentTimeMillis();
    }

    @Override
    public void seek(String playerId, double seconds) {
        startedAt = (long) (System.currentTimeMillis() - seconds * 1000);
        pausedAt = seconds;
    }

    @Override
    public void setVolume(String playerId, int percent) {
        // nothing to change in the demo
    }

    @Override
    public void close() {
    }
}
