package dev.sweety.packet.processor;

import dev.sweety.packet.processor.fixture.LocationPacketDefPacket;
import dev.sweety.packet.processor.fixture.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketSerializableTest {

    @Test
    void roundTrip_abstractEncoderDecoderField() {
        Point original = new Point(3, 7);

        // Write constructor encodes all fields into the internal buffer
        LocationPacketDefPacket written = new LocationPacketDefPacket("spawn", original);

        // Read constructor decodes from raw bytes
        LocationPacketDefPacket read = new LocationPacketDefPacket(written.name(), written.position());
        read.assignTimestamp(written.timestamp());

        assertEquals("spawn", read.name());
        assertEquals(original, read.position());
    }
}
