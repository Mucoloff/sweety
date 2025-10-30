package dev.sweety.network.cloud.packet.incoming;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface CallableDecoder<T> {

    T read(PacketBuffer buffer);

}
