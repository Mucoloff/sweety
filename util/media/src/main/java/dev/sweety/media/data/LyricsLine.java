package dev.sweety.media.data;

/** One timed line of an LRC lyrics file. */
public record LyricsLine(double timeSeconds, String text) {
}
