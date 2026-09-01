package dev.sweety.netty.packet.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacketSequenceGuardTest {

    @Test
    public void testStrictMonotonicSequence() {
        PacketSequenceGuard guard = new PacketSequenceGuard(PacketSequenceGuard.Policy.STRICT_MONOTONIC, 0);

        assertNull(guard.validateSequence(0));
        assertNull(guard.validateSequence(1));
        assertNull(guard.validateSequence(2));

        // Skip sequence 3 -> tampering detected
        PacketSequenceGuard.Violation violation = guard.validateSequence(4);
        assertNotNull(violation);
        assertEquals(3, violation.expectedSequence());
        assertEquals(4, violation.actualSequence());
    }

    @Test
    public void testSlidingWindowSequence() {
        PacketSequenceGuard guard = new PacketSequenceGuard(PacketSequenceGuard.Policy.SLIDING_WINDOW, 5);

        assertNull(guard.validateSequence(0));
        assertNull(guard.validateSequence(2)); // slight reorder allowed
        assertNull(guard.validateSequence(1)); // received in window

        // Duplicate
        PacketSequenceGuard.Violation duplicate = guard.validateSequence(1);
        assertNotNull(duplicate);
        assertTrue(duplicate.reason().contains("Duplicate"));

        // Way too old sequence
        assertNull(guard.validateSequence(10));
        PacketSequenceGuard.Violation tooOld = guard.validateSequence(2);
        assertNotNull(tooOld);
        assertTrue(tooOld.reason().contains("too old"));
    }
}
