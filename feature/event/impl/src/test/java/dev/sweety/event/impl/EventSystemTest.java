package dev.sweety.event.impl;

import dev.sweety.event.api.AbstractCancellableEvent;
import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.test.MutablePlayerJoinEvent;
import dev.sweety.event.test.PlayerJoinEvent;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
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

        system.subscribe(TestEvent.class, e -> order.add("first"), 10, State.PRE);
        system.subscribe(TestEvent.class, e -> order.add("second"), 5, State.PRE);

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
        }, 5, State.PRE);

        system.subscribe(TestEvent.class, e -> order.add("after"), 10, State.PRE);

        TestEvent event = new TestEvent();
        system.dispatch(event);

        assertTrue(event.isCancelled());
        assertEquals(1, order.size());
        assertEquals("canceller", order.getFirst());
    }

    @Test
    void testContainerSubscription() {
        TestContainer container = new TestContainer();
        system.subscribe(container);

        system.dispatch(new TestEvent());

        assertTrue(container.called);
    }

    @Test
    void testErrorPaths() {
        assertThrows(NullPointerException.class, () -> system.subscribe(null, _ -> {
        }, 0, State.PRE));
        assertThrows(NullPointerException.class, () -> system.subscribe(TestEvent.class, null, 0, State.PRE));
        assertThrows(NullPointerException.class, () -> system.subscribe(TestEvent.class, _ -> {
        }, 0, null));
        assertThrows(NullPointerException.class, () -> system.dispatch(null));
        assertThrows(NullPointerException.class, () -> system.subscribe(null));
    }

    @Test
    void testEventAPI() {
        // Test static factory methods injected by processor
        PlayerJoinEvent ev = Event.ofMutable(PlayerJoinEvent.class, "NomeUtente", 42);

        TestContainer container = new TestContainer();
        system.subscribe(container);

        System.out.println("Original: " + ev);

        // Standard listener (Read-Only interface)
        system.on(PlayerJoinEvent.class).handle(e -> {
            System.out.println("Read-only view: " + e.getUsername() + ": " + e.getLevel());
        });

        // Mutable listener (Mutable interface)
        system.on(MutablePlayerJoinEvent.class).handle(e -> {
            e.setUsername(e.getUsername() + "_test");
            System.out.println("Mutated name to: " + e.getUsername());
        });

        // Verifying changes in another listener
        system.on(PlayerJoinEvent.class).priority(10).handle(e -> {
            System.out.println("Post-mutation view: " + e.getUsername() + ": " + e.getLevel());
        });

        system.dispatch(ev);

        assertEquals("NomeUtente_test_linked", ev.getUsername());
    }

    public static class TestEvent extends AbstractCancellableEvent<TestEvent> implements MutableEvent<TestEvent> {

        @Override
        public @NotNull TestEvent toImmutable() {
            return new TestEvent();
        }
    }

    public static class TestContainer {
        boolean called = false;

        @LinkEvent(priority = 1)
        public Listener<TestEvent> onTest = e -> called = true;

        @LinkEvent
        public Listener<MutablePlayerJoinEvent> onPlayerJoin = e -> e.setUsername(e.getUsername() + "_linked");
    }
}
