package dev.sweety.media;

import dev.sweety.media.data.AudioStream;

import java.util.List;

/** Platform specific view of the system mixer's output streams. */
public interface AudioChannelMonitor extends AutoCloseable {

    void start();

    /** Current playback streams. Never null, may be empty. */
    List<AudioStream> streams();

    /**
     * False when this platform cannot really tell which stream is audible, in
     * which case the selector must not require an audio-channel match or it
     * would reject every player.
     */
    default boolean isReliable() {
        return true;
    }

    @Override
    void close();
}
