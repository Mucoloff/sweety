package dev.sweety.netty.messaging;

import dev.sweety.netty.messaging.transport.EndpointRegistry;
import dev.sweety.netty.messaging.transport.SlidingWindowSequenceGuard;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class SlidingWindowSequenceGuardTest {

    @Test
    public void testStrictMonotonicIncrements() {
        SlidingWindowSequenceGuard guard = new SlidingWindowSequenceGuard();

        assertTrue(guard.accept(0));
        assertTrue(guard.accept(1));
        assertTrue(guard.accept(2));
        assertTrue(guard.accept(3));
        assertEquals(3, guard.maxSeq());
    }

    @Test
    public void testOutOfOrderWithinWindowAccepted() {
        SlidingWindowSequenceGuard guard = new SlidingWindowSequenceGuard();

        assertTrue(guard.accept(10));
        assertTrue(guard.accept(8));  // Out of order: 8 arrived after 10 (diff = 2 < 64) -> accepted!
        assertTrue(guard.accept(9));  // Out of order: 9 arrived after 10 (diff = 1 < 64) -> accepted!
        assertTrue(guard.accept(11)); // Higher seq -> accepted!

        // Duplicates must be rejected
        assertFalse(guard.accept(10), "Duplicate 10 must be rejected");
        assertFalse(guard.accept(8), "Duplicate 8 must be rejected");
        assertFalse(guard.accept(9), "Duplicate 9 must be rejected");
        assertFalse(guard.accept(11), "Duplicate 11 must be rejected");
    }

    @Test
    public void testPacketOutsideWindowRejected() {
        SlidingWindowSequenceGuard guard = new SlidingWindowSequenceGuard();

        assertTrue(guard.accept(100));

        // Packet 35 is 65 frames behind 100 (diff >= 64) -> rejected
        assertFalse(guard.accept(35));
        assertFalse(guard.accept(36));

        // Packet 37 is 63 frames behind 100 (diff < 64) -> accepted
        assertTrue(guard.accept(37));
        // And duplicate 37 rejected
        assertFalse(guard.accept(37));
    }

    @Test
    public void testEndpointRegistryIntegration() {
        EndpointRegistry registry = new EndpointRegistry();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 12345);

        registry.register(1L, addr);
        assertEquals(1L, registry.connectionIdFor(addr));
        assertEquals(addr, registry.endpointFor(1L));

        assertTrue(registry.acceptSeq(1L, 5));
        assertTrue(registry.acceptSeq(1L, 3)); // Out of order inside window
        assertFalse(registry.acceptSeq(1L, 5)); // Duplicate rejected

        registry.remove(1L);
        assertEquals(-1L, registry.connectionIdFor(addr));
    }
}
