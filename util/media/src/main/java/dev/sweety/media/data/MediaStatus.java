package dev.sweety.media.data;

/**
 * Everything the overlay needs to draw one track.
 *
 * @param artworkPath local file path or http(s) URL of the cover art
 * @param trackId     stable identity of the track, used for artwork caching
 */
public record MediaStatus(
        String title,
        String artist,
        String album,
        double positionSeconds,
        double durationSeconds,
        String artworkPath,
        String trackId,
        boolean isPlaying,
        boolean shuffleState,
        int volumePercent,
        String repeatState,
        String sourceName) {

    public static final MediaStatus EMPTY =
            new MediaStatus("", "", "", 0, 0, null, "", false, false, 100, "none", "");

    public boolean hasMedia() {
        return !title.isEmpty() || !artist.isEmpty();
    }

    public boolean hasArtwork() {
        return artworkPath != null && !artworkPath.isBlank();
    }

    public float progress() {
        if (durationSeconds <= 0) {
            return 0f;
        }
        return (float) Math.clamp(positionSeconds / durationSeconds, 0.0, 1.0);
    }

    public MediaStatus withPosition(double seconds) {
        return new MediaStatus(title, artist, album, seconds, durationSeconds, artworkPath,
                trackId, isPlaying, shuffleState, volumePercent, repeatState, sourceName);
    }
}
