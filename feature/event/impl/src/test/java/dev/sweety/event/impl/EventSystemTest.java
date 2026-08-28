package dev.sweety.event.impl;

import dev.sweety.event.api.AbstractCancellableEvent;
import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.LinkEvent;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.test.MutablePlayerJoinedEvent;
import dev.sweety.event.test.PlayerJoinedEvent;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.sweety.thread.ThreadManager;

class EventSystemTest {

    private EventSystem system;

    @BeforeEach
    void setUp() {
        system = new EventSystem();
    }

    @Test
    void samePriorityMutablePreservesSubscribeOrder() {
        List<String> order = new ArrayList<>();
        system.subscribe(TestEvent.class, e -> order.add("a"), 5, State.PRE);
        system.subscribe(TestEvent.class, e -> order.add("b"), 5, State.PRE);
        system.dispatch(new TestEvent());
        assertEquals(List.of("a", "b"), order);
    }

    @Test
    void dispatchAfterSubscribeOnSupertypeRebuildsPlan() {
        List<String> hits = new ArrayList<>();
        system.subscribe(ParentEvent.class, e -> hits.add("first"), 0, State.PRE);
        system.dispatch(new ChildEvent());
        assertEquals(List.of("first"), hits);

        hits.clear();
        system.subscribe(ParentEvent.class, e -> hits.add("second"), 10, State.PRE);
        system.dispatch(new ChildEvent());
        assertEquals(List.of("first", "second"), hits);
    }

    @Test
    void customExecutorRunsParallelListeners() {
        try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
            EventSystem es = new EventSystem(new ThreadManager("event-test-exec"), exec);
            AtomicInteger count = new AtomicInteger();
            es.subscribe(ImmutableParallelEvent.class, e -> count.incrementAndGet(), 0, State.PRE);
            es.subscribe(ImmutableParallelEvent.class, e -> count.incrementAndGet(), 0, State.PRE);
            es.dispatch(new ImmutableParallelEvent());
            assertEquals(2, count.get());
        }
    }

    @Test
    void dispatchWrappedRunsOriginalBetweenPreAndPost() {
        List<String> phases = new ArrayList<>();
        system.subscribe(TestEvent.class, e -> phases.add("pre"), 0, State.PRE);
        system.subscribe(TestEvent.class, e -> phases.add("post"), 0, State.POST);
        TestEvent ev = new TestEvent();
        Pair<TestEvent, String> result = system.dispatchWrapped(ev, args -> {
            phases.add("op");
            return "done";
        });
        assertEquals("done", result.right());
        assertEquals(List.of("pre", "op", "post"), phases);
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
        assertThrows(NullPointerException.class, () -> system.subscribe(null, ignored -> {
        }, 0, State.PRE));
        assertThrows(NullPointerException.class, () -> system.subscribe(TestEvent.class, null, 0, State.PRE));
        assertThrows(NullPointerException.class, () -> system.subscribe(TestEvent.class, ignored2 -> {
        }, 0, null));
        assertThrows(NullPointerException.class, () -> system.dispatch(null));
        assertThrows(NullPointerException.class, () -> system.subscribe(null));
    }

    @Test
    void testEventAPI() {
        // Test static factory methods injected by processor
        MutablePlayerJoinedEvent ev = Event.ofMutable(PlayerJoinedEvent.class, "NomeUtente", 42);

        TestContainer container = new TestContainer();
        system.subscribe(container);

        System.out.println("Original: " + ev);

        // Standard listener (Read-Only interface)
        system.on(PlayerJoinedEvent.class).handle(e -> {
            System.out.println("Read-only view: " + e.username() + ": " + e.getlevel());
        });

        // Mutable listener (Mutable interface)
        system.on(MutablePlayerJoinedEvent.class).handle(e -> {
            e.username(e.username() + "_test");
            e.setlevel(e.getlevel() + 1);
            System.out.println("Mutated name to: " + e.username());
        });

        // Verifying changes in another listener
        system.on(PlayerJoinedEvent.class).priority(10).handle(e -> {
            System.out.println("Post-mutation view: " + e.username() + ": " + e.getlevel());
        });

        system.dispatch(ev);

        assertEquals("NomeUtente_test_linked", ev.username());
    }

    public static class TestEvent extends AbstractCancellableEvent<TestEvent> implements MutableEvent<TestEvent> {

        @Override
        public @NotNull TestEvent toImmutable() {
            return new TestEvent();
        }
    }

    public static class ParentEvent extends AbstractCancellableEvent<ParentEvent> implements MutableEvent<ParentEvent> {
        @Override
        public @NotNull ParentEvent toImmutable() {
            return new ParentEvent();
        }
    }

    public static class ChildEvent extends ParentEvent {
    }

    public static class ImmutableParallelEvent extends AbstractCancellableEvent<ImmutableParallelEvent> {
    }

    public static class TestContainer {
        boolean called = false;

        @LinkEvent(priority = 1)
        public Listener<TestEvent> onTest = e -> called = true;

        @LinkEvent
        public Listener<MutablePlayerJoinedEvent> onPlayerJoin = e -> e.username(e.username() + "_linked");
    }
}
