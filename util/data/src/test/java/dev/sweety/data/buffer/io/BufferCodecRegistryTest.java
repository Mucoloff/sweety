package dev.sweety.data.buffer.io;

import dev.sweety.data.buffer.NioBuffer;
import dev.sweety.data.buffer.io.callable.AbstractCallableDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BufferCodecRegistryTest {

    // ── Strategy 1: static AbstractCallableDecoder field ─────────────────────

    static final class FieldDecoded {
        final int value;
        public static final AbstractCallableDecoder<FieldDecoded> DECODER =
                buf -> new FieldDecoded(buf.readInt());
        private FieldDecoded(int value) { this.value = value; }
    }

    // ── Strategy 2: static no-arg method returning AbstractCallableDecoder ───

    static final class MethodDecoded {
        final String name;
        private MethodDecoded(String name) { this.name = name; }
        public static AbstractCallableDecoder<MethodDecoded> decoder() {
            return buf -> new MethodDecoded(buf.readString());
        }
    }

    // ── Strategy 3: static factory method(BufferReader) → T ─────────────────

    static final class FactoryDecoded {
        final boolean flag;
        private FactoryDecoded(boolean flag) { this.flag = flag; }
        public static FactoryDecoded fromBuffer(dev.sweety.data.buffer.BufferReader buf) {
            return new FactoryDecoded(buf.readBoolean());
        }
    }

    // ── Strategy 4: constructor(BufferReader) ────────────────────────────────

    static final class ConstructorDecoded {
        final long timestamp;
        public ConstructorDecoded(dev.sweety.data.buffer.BufferReader buf) {
            this.timestamp = buf.readLong();
        }
    }

    // ── Manual registration ───────────────────────────────────────────────────

    static final class ManualDecoded {
        final double score;
        ManualDecoded(double score) { this.score = score; }
    }

    @BeforeEach
    void clearRegistrations() {
        // Ensure manual-registered entries don't bleed across tests.
        // decoderFor uses computeIfAbsent so we register ManualDecoded explicitly.
        BufferCodecRegistry.register(ManualDecoded.class, buf -> new ManualDecoded(buf.readDouble()));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void strategyField_decodesViaStaticCallableDecoderField() {
        var buf = NioBuffer.heap();
        buf.writeInt(99);

        FieldDecoded result = BufferCodecRegistry.decode(FieldDecoded.class, buf);
        assertEquals(99, result.value);
    }

    @Test
    void strategyMethod_decodesViaStaticNoArgMethodReturningDecoder() {
        var buf = NioBuffer.heap();
        buf.writeString("hello");

        MethodDecoded result = BufferCodecRegistry.decode(MethodDecoded.class, buf);
        assertEquals("hello", result.name);
    }

    @Test
    void strategyFactory_decodesViaStaticFactoryMethod() {
        var buf = NioBuffer.heap();
        buf.writeBoolean(true);

        FactoryDecoded result = BufferCodecRegistry.decode(FactoryDecoded.class, buf);
        assertTrue(result.flag);
    }

    @Test
    void strategyConstructor_decodesViaBufferReaderConstructor() {
        var buf = NioBuffer.heap();
        buf.writeLong(Long.MAX_VALUE);

        ConstructorDecoded result = BufferCodecRegistry.decode(ConstructorDecoded.class, buf);
        assertEquals(Long.MAX_VALUE, result.timestamp);
    }

    @Test
    void manualRegistration_overridesDiscovery() {
        var buf = NioBuffer.heap();
        buf.writeDouble(1.23);

        ManualDecoded result = BufferCodecRegistry.decode(ManualDecoded.class, buf);
        assertEquals(1.23, result.score, 1e-9);
    }

    @Test
    void decoderForReturnsSameInstanceOnRepeatedCalls() {
        assertSame(
                BufferCodecRegistry.decoderFor(FieldDecoded.class),
                BufferCodecRegistry.decoderFor(FieldDecoded.class));
    }

    @Test
    void noDiscoverableDecoder_throwsRuntimeException() {
        class NoDecoder {}
        assertThrows(RuntimeException.class,
                () -> BufferCodecRegistry.decoderFor(NoDecoder.class));
    }
}
