package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

public interface AbstractEncoder {

    void write(final PacketBuffer buffer);

}
