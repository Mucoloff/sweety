package dev.sweety.netty.packet.buffer;

import dev.sweety.data.buffer.AbstractBuffer;
import dev.sweety.data.buffer.NioBuffer;
import dev.sweety.data.buffer.SegmentBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-buffer interop: all three types share big-endian wire format,
 * so bytes written by type A must be readable by type B without conversion.
 */
class BufferInteropTest {

    @FunctionalInterface
    interface Factory { AbstractBuffer<?> create(); }

    static Stream<Arguments> allPairs() {
        Factory[] factories = {
                PacketBuffer::new,
                NioBuffer::heap,
                NioBuffer::direct,
                SegmentBuffer::confined,
                SegmentBuffer::shared
        };
        String[] names = {"PacketBuffer", "NioBuffer.heap", "NioBuffer.direct", "SegmentBuffer.confined", "SegmentBuffer.shared"};

        var builder = Stream.<Arguments>builder();
        for (int w = 0; w < factories.length; w++) {
            for (int r = 0; r < factories.length; r++) {
                builder.add(Arguments.of(names[w] + " → " + names[r], factories[w], factories[r]));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allPairs")
    void crossBufferRoundTrip(String name, Factory writerFactory, Factory readerFactory) {
        AbstractBuffer<?> writer = writerFactory.create();

        writer.writeVarInt(12345);
        writer.writeString("interop");
        writer.writeDouble(Math.PI);
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeBoolean(true);
        writer.writeIntArray(10, 20, 30);
        writer.writeUuid(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        byte[] bytes = writer.getBytes();

        AbstractBuffer<?> reader = readerFactory.create();
        reader.writeBytes(bytes);

        assertEquals(12345,     reader.readVarInt());
        assertEquals("interop", reader.readString());
        assertEquals(Math.PI,   reader.readDouble(), 1e-10);
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertArrayEquals(new int[]{10, 20, 30}, reader.readIntArray());
        assertEquals(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), reader.readUuid());
    }

    // ===== Zero-copy bridge tests =====

    @Test
    void packetBuffer_asNioBuffer_zerocopy() {
        PacketBuffer writer = new PacketBuffer();
        writer.writeInt(0xCAFEBABE);
        writer.readerIndex(0);

        ByteBuffer bb = writer.asNioBuffer();
        assertEquals(0xCAFE, bb.getShort() & 0xFFFF);
        assertEquals(0xBABE, bb.getShort() & 0xFFFF);
    }

    @Test
    void packetBuffer_wrapByteBuffer_roundtrip() {
        NioBuffer nio = NioBuffer.heap();
        nio.writeVarInt(777).writeString("wrapped");
        ByteBuffer bb = nio.asNioBuffer();

        PacketBuffer pkt = PacketBuffer.wrap(bb);
        assertEquals(777,       pkt.readVarInt());
        assertEquals("wrapped", pkt.readString());
    }

    @Test
    void segmentBuffer_asNioBuffer_zerocopy() {
        SegmentBuffer seg = SegmentBuffer.confined();
        seg.writeInt(0xDEADBEEF);
        seg.readerIndex(0);

        ByteBuffer bb = seg.asNioBuffer();
        assertEquals(0xDEAD, bb.getShort() & 0xFFFF);
        assertEquals(0xBEEF, bb.getShort() & 0xFFFF);
    }

    @Test
    void segmentBuffer_wrap_nioBuffer_roundtrip() {
        NioBuffer nio = NioBuffer.direct(32);
        nio.writeVarInt(99).writeString("from nio");
        ByteBuffer bb = nio.asNioBuffer();

        SegmentBuffer seg = SegmentBuffer.wrap(bb);
        assertEquals(99,          seg.readVarInt());
        assertEquals("from nio",  seg.readString());
    }

    @Test
    void nioBuffer_fromSegment_roundtrip() {
        // Write data via a regular SegmentBuffer, copy bytes into a native MemorySegment,
        // then read via NioBuffer.fromSegment to exercise the full bridge path.
        try (Arena arena = Arena.ofConfined()) {
            SegmentBuffer writer = SegmentBuffer.confined(64);
            writer.writeVarInt(42).writeString("native");
            byte[] data = writer.getBytes();

            MemorySegment rawSeg = arena.allocate(64);
            MemorySegment.copy(data, 0, rawSeg, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, data.length);

            NioBuffer nio = NioBuffer.fromSegment(rawSeg);
            // writerIndex = segment capacity (64), data starts at offset 0
            assertEquals(42,       nio.readVarInt());
            assertEquals("native", nio.readString());
        }
    }

    @Test
    void allThreeEndianessMatch() {
        // Same int written by each type must produce identical bytes
        int val = 0x01020304;

        PacketBuffer pkt = new PacketBuffer();
        pkt.writeVarInt(val);
        byte[] pktBytes = pkt.getBytes();

        NioBuffer nio = NioBuffer.heap();
        nio.writeVarInt(val);
        byte[] nioBytes = nio.getBytes();

        SegmentBuffer seg = SegmentBuffer.confined();
        seg.writeVarInt(val);
        byte[] segBytes = seg.getBytes();

        assertArrayEquals(pktBytes, nioBytes,  "PacketBuffer vs NioBuffer endianness mismatch");
        assertArrayEquals(pktBytes, segBytes,  "PacketBuffer vs SegmentBuffer endianness mismatch");
    }
}
