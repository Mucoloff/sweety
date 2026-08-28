package dev.sweety.media;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.Optional;

/**
 * Relates two process ids that belong to the same application.
 *
 * <p>Browsers are the reason this exists: Brave publishes MPRIS from its main
 * process (e.g. pid 9771) while the audio stream on the mixer belongs to its
 * audio-service child (e.g. pid 10372). Neither is the parent of the other —
 * they only share an ancestor. Uses {@link ProcessHandle}, so it works the same
 * on Linux, Windows and macOS.
 *
 * <p>Pid chains are {@link LongList}s rather than {@code List<Long>}: this runs
 * for every player against every mixer stream, on every selection pass.
 */
public final class ProcessTree {

    /** Nobody nests deeper than this in practice; keeps us away from pid 1. */
    private static final int MAX_DEPTH = 8;

    private ProcessTree() {
    }

    /** The pid itself followed by its ancestors, closest first. */
    public static LongList selfAndAncestors(long pid) {
        LongArrayList chain = new LongArrayList(MAX_DEPTH);
        if (pid <= 1) {
            return chain;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        int depth = 0;
        while (handle.isPresent() && depth++ < MAX_DEPTH) {
            long current = handle.get().pid();
            if (current <= 1) {
                break;
            }
            chain.add(current);
            handle = handle.get().parent();
        }
        return chain;
    }

    /**
     * True when both pids belong to the same application: one is an ancestor of
     * the other, or they share an ancestor below the session leader.
     */
    public static boolean sameApplication(long a, long b) {
        return distance(a, b) != Integer.MAX_VALUE;
    }

    /**
     * How closely two pids are related; lower is closer, {@link Integer#MAX_VALUE}
     * when unrelated. Used to break ties when several audio streams could match
     * the same player.
     */
    public static int distance(long a, long b) {
        return distance(a, selfAndAncestors(a), b, selfAndAncestors(b));
    }

    /** Same as {@link #distance(long, long)}, for callers that already hold both chains. */
    public static int distance(long a, LongList chainA, long b, LongList chainB) {
        if (a <= 1 || b <= 1) {
            return Integer.MAX_VALUE;
        }
        if (a == b) {
            return 0;
        }
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < chainA.size(); i++) {
            int j = chainB.indexOf(chainA.getLong(i));
            if (j >= 0) {
                best = Math.min(best, i + j);
            }
        }
        return best;
    }
}
