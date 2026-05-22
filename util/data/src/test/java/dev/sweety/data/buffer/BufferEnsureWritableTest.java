package dev.sweety.data.buffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BufferEnsureWritableTest {

    // ===== NioBuffer =====

    @Test
    void nio_growsHeap() {
        NioBuffer buf = NioBuffer.heap(4);
        for (int i = 0; i < 64; i++) buf.writeVarInt(i);
        buf.readerIndex(0);
        for (int i = 0; i < 64; i++) assertEquals(i, buf.readVarInt());
    }

    @Test
    void nio_growsDirect() {
        NioBuffer buf = NioBuffer.direct(4);
        for (int i = 0; i < 64; i++) buf.writeVarInt(i);
        buf.readerIndex(0);
        for (int i = 0; i < 64; i++) assertEquals(i, buf.readVarInt());
    }

    @Test
    void nio_cannotGrowShared() {
        NioBuffer buf = NioBuffer.heap(16);
        buf.retain();
        assertThrows(IllegalStateException.class, () -> buf.ensureWritable(1024));
    }

    @Test
    void nio_growPreservesContent() {
        NioBuffer buf = NioBuffer.heap(4);
        buf.writeInt(0xDEADBEEF);
        // trigger grow
        buf.writeInt(0xCAFEBABE);
        buf.readerIndex(0);
        assertEquals(0xDEADBEEF, buf.readInt());
        assertEquals(0xCAFEBABE, buf.readInt());
    }

    // ===== SegmentBuffer =====

    @Test
    void segment_grows() {
        SegmentBuffer buf = SegmentBuffer.confined(4);
        for (int i = 0; i < 64; i++) buf.writeVarInt(i);
        buf.readerIndex(0);
        for (int i = 0; i < 64; i++) assertEquals(i, buf.readVarInt());
    }

    @Test
    void segment_cannotGrowShared() {
        SegmentBuffer buf = SegmentBuffer.confined(16);
        buf.retain();
        assertThrows(IllegalStateException.class, () -> buf.ensureWritable(1024));
    }

    @Test
    void segment_cannotGrowNonOwning() {
        SegmentBuffer buf = SegmentBuffer.confined(32);
        buf.writeString("hello");
        SegmentBuffer slice = buf.slice(0, 32);
        assertThrows(IllegalStateException.class, () -> slice.ensureWritable(1024));
    }
}
