package dev.sweety.netty.packet.buffer.io;

import dev.sweety.netty.packet.buffer.PacketBuffer;

import java.util.function.Consumer;

public interface Decoder extends Consumer<PacketBuffer> {

    void read(PacketBuffer buffer);

    @Override
    default void accept(PacketBuffer buffer){
        read(buffer);
    }

}
