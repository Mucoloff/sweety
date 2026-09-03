package dev.sweety.packet.processor;

import dev.sweety.data.buffer.NioBuffer;
import dev.sweety.packet.processor.fixture.PlayerMovePacketImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PacketCodegenTest {

    @Test
    public void testGeneratedPacketSerializationAndDefaultMethod() {
        PlayerMovePacketImpl packet = PlayerMovePacketImpl.of(3.0, 4.0, 0.0, 180.0f, 45.0f, true);

        // Verify getter methods
        assertEquals(3.0, packet.x());
        assertEquals(4.0, packet.y());
        assertEquals(0.0, packet.z());
        assertEquals(180.0f, packet.yaw());
        assertEquals(45.0f, packet.pitch());
        assertTrue(packet.onGround());

        // Verify default method on interface works and was NOT generated as a serialized field
        assertEquals(25.0, packet.distanceSquared(), 0.001);

        // Verify write and read roundtrip
        NioBuffer buffer = NioBuffer.heap(128);
        packet.write(buffer);

        PlayerMovePacketImpl readPacket = new PlayerMovePacketImpl();
        readPacket.read(buffer);

        assertEquals(3.0, readPacket.x());
        assertEquals(4.0, readPacket.y());
        assertEquals(0.0, readPacket.z());
        assertEquals(180.0f, readPacket.yaw());
        assertEquals(45.0f, readPacket.pitch());
        assertTrue(readPacket.onGround());
    }

    @Test
    public void testGeneratedPacketPooling() {
        PlayerMovePacketImpl p1 = PlayerMovePacketImpl.acquire(1.0, 2.0, 3.0, 10.0f, 20.0f, false);
        assertEquals(1.0, p1.x());

        p1.release();

        PlayerMovePacketImpl p2 = PlayerMovePacketImpl.acquire(5.0, 6.0, 7.0, 30.0f, 40.0f, true);
        assertSame(p1, p2, "Pooled instance should be reused on acquire");
        assertEquals(5.0, p2.x());
        assertTrue(p2.onGround());
        p2.release();
    }
}
