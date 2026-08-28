package dev.sweety.media.platform.macos;

import dev.sweety.media.AudioChannelMonitor;
import dev.sweety.media.data.AudioStream;

import java.util.List;

/**
 * macOS has no user-space API that lists which process is currently feeding the
 * output device (the CoreAudio process taps of macOS 14.4+ need an entitlement
 * and TCC consent). This monitor therefore reports nothing and declares itself
 * unreliable, so selection falls back to "playing with artwork".
 */
public final class CoreAudioMonitor implements AudioChannelMonitor {

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
