package dev.sweety.cloud.packet.buffer.io;

import dev.sweety.cloud.packet.buffer.PacketBuffer;

public interface Encoder {

    void write(PacketBuffer buffer);

}
