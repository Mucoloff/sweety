package dev.sweety.netty.messaging.leak;

import dev.sweety.math.pool.leak.ResourceLeakDetector;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacketLeakDetectionTest {

    @Test
    public void testPacketLeakDetectionPassThrough() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

        EmbeddedChannel channel = new EmbeddedChannel(PacketLeakDetectionHandler.instance());

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer(64));
        buf.writeVarInt(42);

        assertTrue(channel.writeInbound(buf));
        PacketBuffer received = channel.readInbound();
        assertNotNull(received);
        assertEquals(42, received.readVarInt());

        received.release();
    }
}
