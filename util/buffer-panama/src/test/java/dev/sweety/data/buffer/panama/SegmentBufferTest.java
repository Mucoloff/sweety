package dev.sweety.data.buffer.panama;

import dev.sweety.data.buffer.NioBuffer;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SegmentBufferTest {

    @Test
    void primitiveReadWriteRoundtrip() {
        SegmentBuffer buf = SegmentBuffer.confined();
        try {
            buf.writeVarInt(99999);
            buf.writeString("panama-ffm-zero-copy");
            buf.writeDouble(Math.PI);
            buf.writeBoolean(true);
            buf.writeBoolean(false);
            buf.writeBoolean(true);
            buf.writeIntArray(10, 20, 30);
            UUID uuid = UUID.randomUUID();
            buf.writeUuid(uuid);

            assertEquals(99999, buf.readVarInt());
            assertEquals("panama-ffm-zero-copy", buf.readString());
            assertEquals(Math.PI, buf.readDouble(), 1e-10);
            assertTrue(buf.readBoolean());
            assertFalse(buf.readBoolean());
            assertTrue(buf.readBoolean());
            assertArrayEquals(new int[]{10, 20, 30}, buf.readIntArray());
            assertEquals(uuid, buf.readUuid());
        } finally {
            buf.release();
        }
    }

    @Test
    void zeroCopyBridgeWithNioBuffer() {
        SegmentBuffer seg = SegmentBuffer.confined();
        try {
            seg.writeVarInt(12345).writeString("seg-to-nio");

            // SegmentBuffer -> NioBuffer zero-copy view
            NioBuffer nio = seg.toNioBuffer();
            assertEquals(12345, nio.readVarInt());
            assertEquals("seg-to-nio", nio.readString());

            // NioBuffer -> SegmentBuffer zero-copy wrap
            NioBuffer nioSrc = NioBuffer.heap();
            nioSrc.writeVarInt(54321).writeString("nio-to-seg");

            SegmentBuffer segFromNio = SegmentBuffer.wrap(nioSrc);
            assertEquals(54321, segFromNio.readVarInt());
            assertEquals("nio-to-seg", segFromNio.readString());
        } finally {
            seg.release();
        }
    }

    @Test
    void rawMemorySegmentInterop() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment raw = arena.allocate(128);
            // Non-owning view for writing: set writerIndex to 0 to write into the pre-allocated segment
            SegmentBuffer seg = SegmentBuffer.wrap(raw);
            seg.writerIndex(0);

            seg.writeInt(0xDEADBEEF);
            seg.writeString("native-c-interop");

            seg.readerIndex(0);
            assertEquals(0xDEAD, seg.readShort() & 0xFFFF);
            assertEquals(0xBEEF, seg.readShort() & 0xFFFF);
            assertEquals("native-c-interop", seg.readString());

            // Direct MemorySegment handle assertion
            assertNotNull(seg.segment());
            assertEquals(128, seg.segment().byteSize());
        }
    }

    @Test
    void pooledAllocatorRecycling() {
        SegmentBuffer buf1 = SegmentBufferAllocator.POOLED.buffer(64);
        buf1.writeString("recycled");
        assertEquals("recycled", buf1.readString());
        buf1.release();

        SegmentBuffer buf2 = SegmentBufferAllocator.POOLED.buffer(64);
        assertEquals(0, buf2.readableBytes());
        buf2.writeVarInt(777);
        assertEquals(777, buf2.readVarInt());
        buf2.release();
    }
}
