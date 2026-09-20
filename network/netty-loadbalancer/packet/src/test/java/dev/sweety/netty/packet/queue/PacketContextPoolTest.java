package dev.sweety.netty.packet.queue;

import dev.sweety.netty.packet.model.Packet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketContextPoolTest {

    private static class DummyPacket extends Packet {
        @Override
        public void write(dev.sweety.data.buffer.BufferWriter writer) {}

        @Override
        public void read(dev.sweety.data.buffer.BufferReader reader) {}
    }

    @Test
    void testPacketContextPoolingAndReset() {
        DummyPacket packet1 = new DummyPacket();
        PacketContext ctx1 = PacketContext.of(packet1, null, 42L);

        assertEquals(packet1, ctx1.packet());
        assertNull(ctx1.ctx());
        assertEquals(42L, ctx1.sequenceId());

        ctx1.release();

        // Reacquire should get the recycled instance or a clean one with reset state
        PacketContext ctx2 = PacketContext.of(new DummyPacket(), null, 99L);
        assertNotNull(ctx2);
        assertEquals(99L, ctx2.sequenceId());
        ctx2.release();
    }
}
