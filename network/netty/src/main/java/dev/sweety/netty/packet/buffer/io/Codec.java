package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.data.buffer.io.AbstractCodec;

public interface Codec extends Encoder, Decoder, AbstractCodec<PacketBuffer> {
}
