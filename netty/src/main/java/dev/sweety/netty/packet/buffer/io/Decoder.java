package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

import java.util.function.Consumer;

public interface Decoder extends Consumer<PacketBuffer> {

    void read(final PacketBuffer buffer);

    @Override
    default void accept(final PacketBuffer buffer){
        read(buffer);
    }

}
