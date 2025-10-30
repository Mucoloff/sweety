package dev.sweety.network.cloud.packet.incoming;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import dev.sweety.network.cloud.packet.model.Packet;

public class PacketIn extends Packet {

    public PacketIn(final PacketIn packetIn) {
        super(packetIn);
    }

    public PacketIn(byte id, Long timestamp, byte[] data) {
        super(id, timestamp, new PacketBuffer(data));
    }

}
