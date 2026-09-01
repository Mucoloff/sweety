package dev.sweety.event.processor;

import dev.sweety.event.processor.fixture.MutablePlayerDamageEvent;
import dev.sweety.event.processor.fixture.PlayerDamageEvent;
import dev.sweety.event.processor.fixture.PlayerDamageEventFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EventCodegenTest {

    @Test
    public void testGeneratedImmutableEvent() {
        PlayerDamageEvent event = PlayerDamageEventFactory.of(42L, 12.5, "magic");

        assertEquals(42L, event.targetId());
        assertEquals(12.5, event.damage());
        assertEquals("magic", event.source());

        // Default interface method works
        assertTrue(event.isCritical());

        // Cancellability
        assertFalse(event.isCancelled());
        event.cancel();
        assertTrue(event.isCancelled());
    }

    @Test
    public void testGeneratedMutableEvent() {
        MutablePlayerDamageEvent mutable = PlayerDamageEventFactory.ofMutable(42L, 8.0, "bow");

        assertEquals(8.0, mutable.damage());
        assertFalse(mutable.isCritical());

        // Mutate damage
        mutable.setDamage(20.0);
        assertEquals(20.0, mutable.damage());
        assertTrue(mutable.isCritical());

        // Convert to immutable
        PlayerDamageEvent immutable = mutable.toImmutable();
        assertEquals(20.0, immutable.damage());
        assertEquals("bow", immutable.source());
    }
}
