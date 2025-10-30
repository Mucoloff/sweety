package dev.sweety.network.cloud.packet.outgoing;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;
import dev.sweety.network.cloud.packet.model.Packet;

public class PacketOut extends Packet {

    public PacketOut(byte id) {
        this(id, null);
    }

    public PacketOut(byte id, Long timestamp) {
        super(id, timestamp, new PacketBuffer());
    }


}
