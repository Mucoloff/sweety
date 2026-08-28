package dev.sweety.media;

import dev.sweety.media.data.AudioStream;
import dev.sweety.media.data.MediaStatus;
import dev.sweety.media.data.PlayerSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Picks the one track to display, from any player, on any platform.
 *
 * <p>The rule the module is built around: a player qualifies only when it is
 * <em>playing</em>, has <em>artwork</em>, and owns a <em>live stream on the
 * system mixer</em>. Artwork is what separates music from a random video or a
 * notification sound; the mixer stream is what separates the thing you are
 * actually hearing from a paused tab in the background.
 */
public final class TrackSelector {

    /** Do not switch away from the current source for this long, to avoid flicker. */
    private static final long SWITCH_HYSTERESIS_MS = 1_500;

    private final MediaSource mediaSource;
    private final AudioChannelMonitor audioMonitor;
    private final boolean requireArtwork;
    private final boolean requireAudioChannel;
    private final boolean requireArtist;

    private String currentPlayerId;
    private long currentSinceMs;
    private MediaStatus lastStatus = MediaStatus.EMPTY;

    public TrackSelector(MediaSource mediaSource, AudioChannelMonitor audioMonitor,
                          boolean requireArtwork, boolean requireAudioChannel,
                          boolean requireArtist) {
        this.mediaSource = mediaSource;
        this.audioMonitor = audioMonitor;
        this.requireArtwork = requireArtwork;
        this.requireAudioChannel = requireAudioChannel;
        this.requireArtist = requireArtist;
    }

    /** @return the selected player, or empty when nothing qualifies right now */
    public Optional<PlayerSnapshot> select(long nowMs) {
        List<PlayerSnapshot> players = mediaSource.players();
        List<AudioStream> streams = audioMonitor.streams();

        // Scoped to this pass: each player's own ancestor chain is walked once,
        // not once per candidate mixer stream (this runs for every player against
        // every stream, on every selection pass).
        Long2ObjectOpenHashMap<LongList> ancestorChains = new Long2ObjectOpenHashMap<>();

        List<Candidate> candidates = players.stream()
                .filter(p -> p.status().hasMedia())
                .filter(p -> p.status().isPlaying())
                .filter(p -> !requireArtwork || p.status().hasArtwork())
                .filter(p -> !requireArtist || isSongLike(p.status()))
                .map(p -> new Candidate(p, matchStream(p, streams, ancestorChains)))
                .filter(c -> !requireAudioChannel || c.stream != null)
                .sorted(Comparator
                        // stick with what we are already showing
                        .comparingInt((Candidate c) -> c.player.id().equals(currentPlayerId) ? 0 : 1)
                        // then the loudest stream: that is the one you are hearing
                        .thenComparingInt(c -> -(c.stream == null ? 0 : c.stream.volumePercent()))
                        // then the closest process match, so a direct hit beats a name guess
                        .thenComparingInt(c -> c.matchDistance))
                .toList();

        if (candidates.isEmpty()) {
            currentPlayerId = null;
            lastStatus = MediaStatus.EMPTY;
            return Optional.empty();
        }

        Candidate best = candidates.getFirst();
        boolean switching = currentPlayerId != null && !best.player.id().equals(currentPlayerId);
        if (switching && nowMs - currentSinceMs < SWITCH_HYSTERESIS_MS) {
            // Too soon to move; keep the previous player if it still qualifies.
            Optional<Candidate> incumbent = candidates.stream()
                    .filter(c -> c.player.id().equals(currentPlayerId))
                    .findFirst();
            if (incumbent.isPresent()) {
                best = incumbent.get();
            }
        }

        if (!best.player.id().equals(currentPlayerId)) {
            currentPlayerId = best.player.id();
            currentSinceMs = nowMs;
        }
        lastStatus = best.player.status();
        return Optional.of(best.player);
    }

    /**
     * A song carries an artist or an album. A video usually carries neither,
     * even when the player hands out a picture for it.
     */
    private static boolean isSongLike(MediaStatus status) {
        return !status.artist().isBlank() || !status.album().isBlank();
    }

    public MediaStatus lastStatus() {
        return lastStatus;
    }

    public String currentPlayerId() {
        return currentPlayerId;
    }

    /**
     * Finds the mixer stream belonging to a player: first by process ancestry,
     * then by application/binary name for platforms that do not report a usable pid.
     */
    private Candidate.Match matchStream(PlayerSnapshot player, List<AudioStream> streams,
                                        Long2ObjectOpenHashMap<LongList> ancestorChains) {
        AudioStream bestStream = null;
        int bestDistance = Integer.MAX_VALUE;

        LongList playerChain = player.pid() > 1
                ? ancestorChains.computeIfAbsent(player.pid(), ProcessTree::selfAndAncestors)
                : new LongArrayList(0);

        for (AudioStream stream : streams) {
            if (!stream.isActive()) {
                continue;
            }
            int distance = Integer.MAX_VALUE;
            if (player.pid() > 1 && stream.pid() > 1) {
                LongList streamChain = ancestorChains.computeIfAbsent(stream.pid(), ProcessTree::selfAndAncestors);
                distance = ProcessTree.distance(player.pid(), playerChain, stream.pid(), streamChain);
            }
            if (distance == Integer.MAX_VALUE && namesMatch(player, stream)) {
                // Name match is weaker than an ancestry hit, so it sorts after it.
                distance = 100;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                bestStream = stream;
            }
        }
        return new Candidate.Match(bestStream, bestDistance);
    }

    private static boolean namesMatch(PlayerSnapshot player, AudioStream stream) {
        return tokensOf(player).stream().anyMatch(token ->
                !token.isBlank() && (contains(stream.binary(), token) || contains(stream.appName(), token)));
    }

    private static List<String> tokensOf(PlayerSnapshot player) {
        // "org.mpris.MediaPlayer2.brave.instance9771" -> "brave"
        String id = player.id();
        int lastDot = id.lastIndexOf('.');
        String tail = lastDot >= 0 ? id.substring(lastDot + 1) : id;
        tail = tail.replaceAll("instance\\d+", "").replaceAll("\\d+$", "");
        return List.of(norm(player.binary()), norm(player.appName()), norm(tail));
    }

    private static boolean contains(String haystack, String needle) {
        if (haystack == null || needle.isBlank()) {
            return false;
        }
        String h = norm(haystack);
        return h.contains(needle) || needle.contains(h);
    }

    private static String norm(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Candidate(PlayerSnapshot player, AudioStream stream, int matchDistance) {
        Candidate(PlayerSnapshot player, Match match) {
            this(player, match.stream(), match.distance());
        }

        record Match(AudioStream stream, int distance) {
        }
    }
}
