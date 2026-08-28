package dev.sweety.media.data;

/**
 * One media player as reported by the platform media source.
 *
 * @param id      opaque identifier, unique per player (MPRIS bus name, SMTC app id, ...)
 * @param pid     process id owning the player, or -1 when the platform does not expose one
 * @param appName human readable application name ("Brave", "Spotify", ...)
 * @param binary  executable name when known, used as a fallback for audio-stream matching
 * @param status  the track currently loaded in this player
 */
public record PlayerSnapshot(
        String id,
        long pid,
        String appName,
        String binary,
        MediaStatus status) {
}
