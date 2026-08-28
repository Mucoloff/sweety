package dev.sweety.media;

import dev.sweety.media.data.PlayerSnapshot;

import java.util.List;

/** Platform specific "what is playing" provider: MPRIS, SMTC, MediaRemote. */
public interface MediaSource extends AutoCloseable {

    /** Starts background listening. Must be cheap to call twice. */
    void start();

    /** Current state of every known player. Never null, may be empty. */
    List<PlayerSnapshot> players();

    void playPause(String playerId);

    void next(String playerId);

    void previous(String playerId);

    /** @param seconds absolute position inside the current track */
    void seek(String playerId, double seconds);

    /** @param percent 0..100; ignored by players that do not expose a volume */
    void setVolume(String playerId, int percent);

    @Override
    void close();
}
