package dev.sweety.media.platform.windows;

import dev.sweety.media.AudioChannelMonitor;
import dev.sweety.media.data.AudioStream;

import java.util.List;

/**
 * Windows audio sessions, as far as the bridge reports them.
 *
 * <p>The shipped bridge exposes only the session SMTC considers current, which
 * already is "the app you are listening to", but it does not enumerate WASAPI
 * sessions per process. So this monitor declares itself unreliable and selection
 * falls back to "playing, with artwork, with an artist" — an extended bridge
 * that reports {@code AudioActive} upgrades it for free.
 */
public final class WasapiMonitor implements AudioChannelMonitor {

    private final SmtcBridge bridge = SmtcBridge.getInstance();

    @Override
    public void start() {
        bridge.start();
    }

    @Override
    public List<AudioStream> streams() {
        return bridge.streams();
    }

    @Override
    public boolean isReliable() {
        return false;
    }

    @Override
    public void close() {
        // The bridge is shared with SmtcSource, which owns its lifetime.
    }
}
