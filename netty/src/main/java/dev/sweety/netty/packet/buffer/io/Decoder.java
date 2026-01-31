package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

public interface Decoder {

    void read(final PacketBuffer buffer);

}
