package dev.sweety.data.buffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferSliceTest {

    // ===== NioBuffer =====

    @Test
    void nio_slice_sharesMemory() {
        NioBuffer buf = NioBuffer.heap(16);
        buf.writeInt(0xDEADBEEF);
        buf.writeInt(0xCAFEBABE);
        buf.readerIndex(0);

        NioBuffer slice = buf.slice(0, 4);
        assertEquals((byte) 0xDE, slice.readByte());
        // mutate via parent's setByte, see through slice
        buf.setByte(0, (byte) 0xFF);
        slice.readerIndex(0);
        assertEquals((byte) 0xFF, slice.readByte());
    }

    @Test
    void nio_readSlice_advancesReader() {
        NioBuffer buf = NioBuffer.heap(8);
        buf.writeInt(1).writeInt(2);
        buf.readerIndex(0);

        NioBuffer s1 = buf.readSlice(4);
        NioBuffer s2 = buf.readSlice(4);
        assertEquals(1, s1.readInt());
        assertEquals(2, s2.readInt());
    }

    @Test
    void nio_retainedSlice_refCnt() {
        NioBuffer buf = NioBuffer.heap(8);
        buf.writeInt(42);
        buf.readerIndex(0);
        NioBuffer s = buf.retainedSlice(0, 4);
        assertEquals(2, s.refCnt()); // slice has its own refCnt starting at 1, +1 from retain
        s.release();
        assertEquals(1, s.refCnt());
    }

    // ===== SegmentBuffer =====

   /* @Test
    void segment_slice_sharesMemory() {
        SegmentBuffer buf = SegmentBuffer.confined(16);
        buf.writeInt(0xDEADBEEF);
        buf.writeInt(0xCAFEBABE);
        buf.readerIndex(0);

        SegmentBuffer slice = buf.slice(0, 4);
        assertEquals((byte) 0xDE, slice.readByte());
        buf.setByte(0, (byte) 0xFF);
        slice.readerIndex(0);
        assertEquals((byte) 0xFF, slice.readByte());
    }

    @Test
    void segment_readSlice_advancesReader() {
        SegmentBuffer buf = SegmentBuffer.confined(8);
        buf.writeInt(10).writeInt(20);
        buf.readerIndex(0);

        SegmentBuffer s1 = buf.readSlice(4);
        SegmentBuffer s2 = buf.readSlice(4);
        assertEquals(10, s1.readInt());
        assertEquals(20, s2.readInt());
    }*/
}
