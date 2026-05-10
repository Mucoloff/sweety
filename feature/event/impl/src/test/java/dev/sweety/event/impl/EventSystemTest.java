package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

class EventSystemTest {

    private EventSystem system;

    @BeforeEach
    void setUp() {
        system = new EventSystem();
    }

    @Test
    void testBasicDispatch() {
        List<String> order = new ArrayList<>();
        
        system.subscribe(TestEvent.class, e -> order.add("first"), 10, dev.sweety.event.api.info.State.PRE);
        system.subscribe(TestEvent.class, e -> order.add("second"), 5, dev.sweety.event.api.info.State.PRE);
        
        system.dispatch(new TestEvent());
        
        assertEquals("second", order.get(0)); // Lower priority value (5) comes first due to sorting
        assertEquals("first", order.get(1));
    }

    @Test
    void testCancellation() {
        List<String> order = new ArrayList<>();
        
        system.subscribe(TestEvent.class, e -> {
            order.add("canceller");
            e.cancel();
        }, 5, dev.sweety.event.api.info.State.PRE);
        
        system.subscribe(TestEvent.class, e -> order.add("after"), 10, dev.sweety.event.api.info.State.PRE);
        
        TestEvent event = new TestEvent();
        system.dispatch(event);
        
        assertTrue(event.isCancelled());
        assertEquals(1, order.size());
        assertEquals("canceller", order.get(0));
    }

    @Test
    void testContainerSubscription() {
        TestContainer container = new TestContainer();
        system.subscribe(container);
        
        system.dispatch(new TestEvent());
        
        assertTrue(container.called);
    }

    public static class TestEvent extends Event {
        public String data = "original";
    }

    public static class TestContainer {
        boolean called = false;
        
        @LinkEvent(priority = 1)
        public Listener<TestEvent> onTest = e -> called = true;
    }
}
