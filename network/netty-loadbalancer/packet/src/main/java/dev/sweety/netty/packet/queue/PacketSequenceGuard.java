package dev.sweety.netty.packet.queue;

import dev.sweety.netty.packet.model.Packet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance anti-tampering sequence and state guard.
 * Validates strict packet sequence monotonicity and protocol phase transitions.
 */
public class PacketSequenceGuard {

    public enum Policy {
        /** Strict monotonicity (TCP): next = current + 1. Any skip or regression = tampering */
        STRICT_MONOTONIC,
        /** Sliding window (UDP/Loss-tolerant): allows minor reordering within window */
        SLIDING_WINDOW
    }

    public static class Violation {
        private final long expectedSequence;
        private final long actualSequence;
        private final String reason;

        public Violation(long expectedSequence, long actualSequence, String reason) {
            this.expectedSequence = expectedSequence;
            this.actualSequence = actualSequence;
            this.reason = reason;
        }

        public long expectedSequence() { return expectedSequence; }
        public long actualSequence() { return actualSequence; }
        public String reason() { return reason; }

        @Override
        public String toString() {
            return "Violation{" + reason + ", expected=" + expectedSequence + ", actual=" + actualSequence + "}";
        }
    }

    private final Policy policy;
    private final int windowSize;
    private final AtomicLong nextSequence = new AtomicLong(0);
    private final Set<Long> receivedInWindow = ConcurrentHashMap.newKeySet();
    private volatile int currentState = 0;

    public PacketSequenceGuard() {
        this(Policy.STRICT_MONOTONIC, 0);
    }

    public PacketSequenceGuard(Policy policy, int windowSize) {
        this.policy = policy;
        this.windowSize = windowSize;
    }

    /**
     * Validates an inbound packet sequence number against current state.
     * @param sequenceId monotonic sequence ID attached to the packet
     * @return null if valid, or a Violation instance if tampering/anomaly detected
     */
    public Violation validateSequence(long sequenceId) {
        if (policy == Policy.STRICT_MONOTONIC) {
            final long expected = nextSequence.getAndIncrement();
            if (sequenceId != expected) {
                return new Violation(expected, sequenceId, "Strict sequence mismatch (possible packet injection or tampering)");
            }
            return null;
        }

        // SLIDING_WINDOW
        final long currentMin = nextSequence.get();
        if (sequenceId < currentMin - windowSize) {
            return new Violation(currentMin, sequenceId, "Sequence too old (outside window)");
        }
        if (!receivedInWindow.add(sequenceId)) {
            return new Violation(currentMin, sequenceId, "Duplicate packet sequence detected");
        }
        if (sequenceId >= currentMin) {
            nextSequence.set(sequenceId + 1);
            receivedInWindow.removeIf(seq -> seq < sequenceId - windowSize);
        }
        return null;
    }

    public int currentState() {
        return currentState;
    }

    public void transitionState(int nextState) {
        this.currentState = nextState;
    }

    public void reset() {
        this.nextSequence.set(0);
        this.receivedInWindow.clear();
        this.currentState = 0;
    }
}
