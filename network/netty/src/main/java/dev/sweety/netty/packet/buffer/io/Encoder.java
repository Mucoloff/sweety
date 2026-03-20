package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

public interface Encoder {

    void write(final PacketBuffer buffer);

}
