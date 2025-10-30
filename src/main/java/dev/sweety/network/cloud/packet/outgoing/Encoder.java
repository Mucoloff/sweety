package dev.sweety.network.cloud.packet.outgoing;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface Encoder {

    void write(PacketBuffer buffer);

}
