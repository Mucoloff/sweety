package dev.sweety.netty.packet.buffer.io;

import dev.sweety.data.buffer.io.AbstractCodec;

/** Netty packet codec combining {@link Encoder} and {@link Decoder}; no concrete buffer type parameter. */
public interface Codec extends Encoder, Decoder, AbstractCodec {
}
