package dev.sweety.network.cloud.packet.buffer.io;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface Decoder {

    void read(PacketBuffer buffer);

}
