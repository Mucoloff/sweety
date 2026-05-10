package dev.sweety.event.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void testCancellation() {
        Event event = new TestEvent();
        assertFalse(event.isCancelled());
        
        event.cancel();
        assertTrue(event.isCancelled());
        
        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    void testPrePost() {
        Event event = new TestEvent();
        assertTrue(event.isPre());
        assertFalse(event.isPost());
        
        event.post();
        assertFalse(event.isPre());
        assertTrue(event.isPost());
    }

    @Test
    void testChanged() {
        Event event = new TestEvent();
        assertFalse(event.isChanged());
        
        event.setChanged(true);
        assertTrue(event.isChanged());
    }

    private static class TestEvent extends Event {}
}
