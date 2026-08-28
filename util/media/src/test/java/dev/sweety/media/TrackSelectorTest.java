package dev.sweety.media;

import dev.sweety.media.data.AudioStream;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackSelectorTest {

    private static final long OWN_PID = ProcessHandle.current().pid();

    @Test
    void picksThePlayerThatHasArtworkAndALiveStream() {
        FakeSource source = new FakeSource(
                player("spotify", OWN_PID, song("Midnight City", "M83", "/tmp/cover.png", true)));
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "Spotify", "spotify", true));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);
        var selected = selector.select(0);

        assertTrue(selected.isPresent());
        assertEquals("Midnight City", selected.get().status().title());
    }

    @Test
    void rejectsAVideoThatHasArtworkButNoArtist() {
        FakeSource source = new FakeSource(
                player("brave", OWN_PID, song("Some YouTube video", "", "/tmp/icon.png", true)));
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "Brave", "brave", true));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertTrue(selector.select(0).isEmpty());
    }

    @Test
    void rejectsAPlayerWithoutArtwork() {
        FakeSource source = new FakeSource(
                player("mpv", OWN_PID, song("Track", "Artist", null, true)));
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "mpv", "mpv", true));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertTrue(selector.select(0).isEmpty());
    }

    @Test
    void rejectsAPausedPlayerEvenWithAStream() {
        FakeSource source = new FakeSource(
                player("spotify", OWN_PID, song("Track", "Artist", "/tmp/cover.png", false)));
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "Spotify", "spotify", true));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertTrue(selector.select(0).isEmpty());
    }

    @Test
    void rejectsAPlayerWhoseStreamIsCorked() {
        FakeSource source = new FakeSource(
                player("spotify", OWN_PID, song("Track", "Artist", "/tmp/cover.png", true)));
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "Spotify", "spotify", false));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertTrue(selector.select(0).isEmpty());
    }

    @Test
    void matchesByApplicationNameWhenThePidsAreUnrelated() {
        // A browser publishes MPRIS from one process and plays audio from another;
        // when the process tree cannot link them, the app name has to.
        FakeSource source = new FakeSource(
                player("org.mpris.MediaPlayer2.brave.instance9771", 999_999,
                        song("Survivor", "Faneto", "/tmp/cover.png", true)));
        FakeMonitor monitor = new FakeMonitor(stream(888_888, "Brave", "brave", true));

        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertTrue(selector.select(0).isPresent());
    }

    @Test
    void keepsTheCurrentPlayerWhileTheHysteresisWindowIsOpen() {
        PlayerSnapshot first = player("a", OWN_PID, song("First", "A", "/tmp/a.png", true));
        PlayerSnapshot second = player("b", OWN_PID, song("Second", "B", "/tmp/b.png", true));
        FakeSource source = new FakeSource(first);
        FakeMonitor monitor = new FakeMonitor(stream(OWN_PID, "player", "player", true));
        TrackSelector selector = new TrackSelector(source, monitor, true, true, true);

        assertEquals("First", selector.select(1_000).orElseThrow().status().title());

        // A louder rival appears immediately; the overlay must not flip straight away.
        source.players = List.of(first, second);
        monitor.streams = List.of(stream(OWN_PID, "player", "player", true));
        assertEquals("First", selector.select(1_500).orElseThrow().status().title());
    }

    @Test
    void selectsNothingWhenNoPlayerIsRunning() {
        TrackSelector selector = new TrackSelector(new FakeSource(), new FakeMonitor(), true, true, true);

        assertTrue(selector.select(0).isEmpty());
    }

    // --- helpers --------------------------------------------------------

    private static MediaStatus song(String title, String artist, String artwork, boolean playing) {
        return new MediaStatus(title, artist, "", 10, 200, artwork, title + artist, playing,
                false, 100, "none", "test");
    }

    private static PlayerSnapshot player(String id, long pid, MediaStatus status) {
        String name = id.contains("brave") ? "Brave" : id;
        return new PlayerSnapshot(id, pid, name, name, status);
    }

    private static AudioStream stream(long pid, String appName, String binary, boolean active) {
        return new AudioStream(1, pid, appName, binary, !active, false, 80);
    }

    private static final class FakeSource implements MediaSource {
        private List<PlayerSnapshot> players;

        FakeSource(PlayerSnapshot... players) {
            this.players = new ArrayList<>(List.of(players));
        }

        @Override
        public void start() {
        }

        @Override
        public List<PlayerSnapshot> players() {
            return players;
        }

        @Override
        public void playPause(String playerId) {
        }

        @Override
        public void next(String playerId) {
        }

        @Override
        public void previous(String playerId) {
        }

        @Override
        public void seek(String playerId, double seconds) {
        }

        @Override
        public void setVolume(String playerId, int percent) {
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeMonitor implements AudioChannelMonitor {
        private List<AudioStream> streams;

        FakeMonitor(AudioStream... streams) {
            this.streams = new ArrayList<>(List.of(streams));
        }

        @Override
        public void start() {
        }

        @Override
        public List<AudioStream> streams() {
            return streams;
        }

        @Override
        public void close() {
        }
    }
}
