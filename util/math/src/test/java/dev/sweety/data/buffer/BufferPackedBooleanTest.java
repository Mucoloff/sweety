package dev.sweety.data.buffer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.sweety.data.buffer.BufferFactories.Factory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BufferPackedBooleanTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void stateIsPerInstance(String name, Factory f) {
        var a = f.create();
        var b = f.create();

        a.writeBoolean(true);
        b.writeBoolean(false);
        // a's mask must not bleed into b's
        assertTrue(a.readBoolean());
        assertFalse(b.readBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void resetOnReaderIndex(String name, Factory f) {
        var buf = f.create();
        buf.writeBoolean(true).writeBoolean(false).writeBoolean(true);
        buf.readBoolean(); // consume one

        // Force reset reader
        buf.readerIndex(0);
        // Re-reading from position 0 should give the first boolean again
        assertTrue(buf.readBoolean());
        assertFalse(buf.readBoolean());
        assertTrue(buf.readBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void resetOnWriterIndex(String name, Factory f) {
        // After writerIndex(x), packed write state clears: next boolean starts a new byte
        var buf = f.create();
        buf.writeBoolean(true); // packed into byte at position 0; writerIndex → 1

        buf.writerIndex(0); // rewind + reset packed write state

        // Fresh start — two booleans packed into byte at position 0 (overwrites old)
        buf.writeBoolean(false);
        buf.writeBoolean(true);

        assertFalse(buf.readBoolean(), "first bool (fresh byte at 0)");
        assertTrue(buf.readBoolean(),  "second bool");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void crossBytePackingBoundary(String name, Factory f) {
        var buf = f.create();
        // Write 16 booleans across 2 mask bytes
        boolean[] vals = new boolean[16];
        for (int i = 0; i < 16; i++) { vals[i] = (i % 3 == 0); buf.writeBoolean(vals[i]); }
        for (int i = 0; i < 16; i++) assertEquals(vals[i], buf.readBoolean(), "bool[" + i + "]");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dev.sweety.data.buffer.BufferFactories#allBuffers")
    void clearResetsState(String name, Factory f) {
        var buf = f.create();
        buf.writeBoolean(true).writeBoolean(false);
        buf.clear();

        buf.writeBoolean(false).writeBoolean(true);
        assertFalse(buf.readBoolean());
        assertTrue(buf.readBoolean());
    }
}
