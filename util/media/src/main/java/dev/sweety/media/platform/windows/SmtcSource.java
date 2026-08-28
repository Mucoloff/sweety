package dev.sweety.media.platform.windows;

import dev.sweety.media.MediaSource;
import dev.sweety.media.data.PlayerSnapshot;

import java.util.List;

/** Windows System Media Transport Controls, driven by the bundled bridge process. */
public final class SmtcSource implements MediaSource {

    private final SmtcBridge bridge = SmtcBridge.getInstance();

    @Override
    public void start() {
        bridge.start();
    }

    @Override
    public List<PlayerSnapshot> players() {
        return bridge.players();
    }

    @Override
    public void playPause(String playerId) {
        bridge.send("playpause");
    }

    @Override
    public void next(String playerId) {
        bridge.send("next");
    }

    @Override
    public void previous(String playerId) {
        bridge.send("previous");
    }

    @Override
    public void seek(String playerId, double seconds) {
        bridge.send("seek " + (long) (seconds * 1000));
    }

    @Override
    public void setVolume(String playerId, int percent) {
        bridge.send("volume " + Math.clamp(percent, 0, 100));
    }

    @Override
    public void close() {
        bridge.stop();
    }
}
