package dev.sweety.media.data;

import java.util.List;

/**
 * Result of a lyrics lookup: synced lines when LRCLIB has them, plain text as
 * a fallback, or neither when the track was not found.
 */
public record Lyrics(List<LyricsLine> synced, String plain) {

    public static final Lyrics NONE = new Lyrics(List.of(), null);

    public boolean isEmpty() {
        return synced.isEmpty() && (plain == null || plain.isBlank());
    }

    public boolean isSynced() {
        return !synced.isEmpty();
    }
}
