package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTest {

    @Test
    void testCancellation() {
        CancellableEvent event = new TestEvent();
        assertFalse(event.isCancelled());

        event.cancel();
        assertTrue(event.isCancelled());

        event.uncancel();
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
        AbstractEvent event = new TestEvent();
        assertFalse(event.isChanged());

        CHANGED.accept(event, true);
        assertTrue(event.isChanged());
    }

    private static class TestEvent extends AbstractCancellableEvent {

        private boolean cancelled = false;

        @Override
        public @NotNull Event toImmutable() {
            return this;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public void uncancel() {
            this.cancelled = false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final BiConsumer<AbstractEvent, Boolean> CHANGED = (event, state) -> {
        try {
            Field changed = AbstractEvent.class.getDeclaredField("changed");
            if (!changed.canAccess(event)) changed.setAccessible(true);
            changed.setBoolean(event, state);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace(System.err);
        }
    };
}
