package dev.sweety.network.cloud.packet.buffer.io;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface Encoder {

    void write(PacketBuffer buffer);

}
