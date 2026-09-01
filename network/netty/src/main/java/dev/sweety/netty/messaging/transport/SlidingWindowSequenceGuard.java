package dev.sweety.netty.messaging.transport;

import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance 64-bit sliding window anti-replay and sequence guard (RFC 6479).
 *
 * <p>Unlike strict monotonic checks (which drop any out-of-order packet even if separated by 1 frame),
 * this guard tolerates network jitter by accepting out-of-order packets within a 64-frame window while
 * strictly preventing duplicates, replays, and ancient packets.
 *
 * <p>Completely lock-free via Atomic CAS updates.
 */
public final class SlidingWindowSequenceGuard {

    private static final int WINDOW_SIZE = 64;

    private static final class State {
        final long maxSeq;
        final long bitmap;

        State(long maxSeq, long bitmap) {
            this.maxSeq = maxSeq;
            this.bitmap = bitmap;
        }
    }

    private final AtomicReference<State> state = new AtomicReference<>(new State(-1L, 0L));

    /**
     * Checks and records whether the given sequence number is valid and acceptable.
     *
     * @param seq sequence number of the received datagram
     * @return {@code true} if accepted (new or valid out-of-order within window); {@code false} if duplicate or too old
     */
    public boolean accept(long seq) {
        if (seq < 0) return false;

        while (true) {
            State current = state.get();
            long maxSeq = current.maxSeq;
            long bitmap = current.bitmap;

            if (maxSeq == -1L) {
                // First packet ever received
                State next = new State(seq, 1L);
                if (state.compareAndSet(current, next)) {
                    return true;
                }
                continue;
            }

            if (seq > maxSeq) {
                long diff = seq - maxSeq;
                long nextBitmap = diff < WINDOW_SIZE ? (bitmap << diff) | 1L : 1L;
                State next = new State(seq, nextBitmap);
                if (state.compareAndSet(current, next)) {
                    return true;
                }
            } else {
                long diff = maxSeq - seq;
                if (diff >= WINDOW_SIZE) {
                    // Packet is older than 64 frames
                    return false;
                }
                long mask = 1L << diff;
                if ((bitmap & mask) != 0) {
                    // Already seen (duplicate replay)
                    return false;
                }
                State next = new State(maxSeq, bitmap | mask);
                if (state.compareAndSet(current, next)) {
                    return true;
                }
            }
        }
    }

    /**
     * Returns the highest sequence number seen so far, or -1 if no packets received yet.
     */
    public long maxSeq() {
        return state.get().maxSeq;
    }

    /**
     * Resets the guard state.
     */
    public void reset() {
        state.set(new State(-1L, 0L));
    }
}
