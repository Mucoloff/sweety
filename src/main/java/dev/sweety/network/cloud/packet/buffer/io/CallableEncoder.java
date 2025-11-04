package dev.sweety.network.cloud.packet.buffer.io;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface CallableEncoder<T> {

    void write(T data, PacketBuffer buffer);

}
