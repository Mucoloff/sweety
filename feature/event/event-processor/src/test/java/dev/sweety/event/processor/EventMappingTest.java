package dev.sweety.event.processor;

import dev.sweety.event.api.*;
import dev.sweety.event.api.listener.Listener;
import dev.sweety.event.api.function.Operation;
import it.unimi.dsi.fastutil.Pair;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class EventMappingTest {

    // Minimal Event impl for testing.
    static final class TestEvent implements Event<TestEvent> {
        final String value;
        public TestEvent(String value) { this.value = value; }
        @Override public TestEvent post() { return this; }
        @Override public boolean isPost() { return false; }
        @Override public boolean isPre() { return true; }
        @Override public boolean isChanged() { return false; }
    }

    // Stub: dispatch just returns the event; everything else throws.
    static IEventSystem passThroughSystem() {
        return new IEventSystem() {
            @Override public <T extends Event<?>> T dispatch(T event) { return event; }
            @Override public <T extends Event<?>> void subscribe(Class<T> t, Listener<T> l, int p, dev.sweety.event.api.info.State s) { throw new UnsupportedOperationException(); }
            @Override public <T extends Event<?>> SubscriptionBuilder<T> on(Class<T> t) { throw new UnsupportedOperationException(); }
            @Override public <T extends Event<?>> void unsubscribe(Class<T> t) { throw new UnsupportedOperationException(); }
            @Override public void subscribe(Object o) { throw new UnsupportedOperationException(); }
            @Override public void unsubscribe(Object o) { throw new UnsupportedOperationException(); }
            @Override public <T extends MutableEvent<?>, R> Pair<T, R> dispatchWrapped(T event, Operation<R> original, Function<T, Object[]> changedArgsMapper, Object... args) {
                @SuppressWarnings("unchecked") T e = (T) dispatch(event);
                return Pair.of(e, original.call(args));
            }
            @Override public <T extends Event<?>, R> Pair<T, R> dispatchWrapped(T event, Operation<R> original, Object... args) {
                T e = dispatch(event);
                return Pair.of(e, original.call(args));
            }
        };
    }

    @Test
    void dispatch_registered_returnsEvent() {
        EventMapping mapping = new EventMapping(passThroughSystem());
        mapping.registerEventMapping(TestEvent.class, String.class);

        TestEvent result = mapping.dispatch("hello");
        assertNotNull(result);
        assertEquals("hello", result.value);
    }

    @Test
    void dispatch_unregistered_returnsNull() {
        EventMapping mapping = new EventMapping(passThroughSystem());
        assertNull(mapping.dispatch("no-mapping"));
    }

    @Test
    void dispatchWrapped_registered_returnsResultPair() {
        EventMapping mapping = new EventMapping(passThroughSystem());
        mapping.registerEventMapping(TestEvent.class, String.class);

        Pair<TestEvent, String> result = mapping.dispatchWrapped(
                "input",
                args -> "computed-" + args[0],
                "arg0"
        );
        assertNotNull(result);
        assertEquals("input", result.left().value);
        assertEquals("computed-arg0", result.right());
    }

    @Test
    void dispatchWrapped_unregistered_returnsNull() {
        EventMapping mapping = new EventMapping(passThroughSystem());
        Pair<?, ?> result = mapping.dispatchWrapped("x", args -> "ignored");
        assertNull(result);
    }

    @Test
    void registerMapping_functionVariant_works() {
        EventMapping mapping = new EventMapping(passThroughSystem());
        mapping.<TestEvent, String>registerEventMapping(String.class, s -> new TestEvent("fn-" + s));

        TestEvent result = mapping.dispatch("world");
        assertNotNull(result);
        assertEquals("fn-world", result.value);
    }
}
