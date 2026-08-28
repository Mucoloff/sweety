package dev.sweety.media.data;

/**
 * One playback stream on the system mixer (a PipeWire/PulseAudio sink-input,
 * a WASAPI session, a CoreAudio process tap).
 *
 * @param corked true when the stream exists but is not currently pushing audio
 */
public record AudioStream(
        int index,
        long pid,
        String appName,
        String binary,
        boolean corked,
        boolean muted,
        int volumePercent) {

    /** A stream is "live" when it is actually producing audible output. */
    public boolean isActive() {
        return !corked && !muted && volumePercent > 0;
    }
}
