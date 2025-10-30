package dev.sweety.network.cloud.packet.incoming;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public interface Decoder {

    void read(PacketBuffer buffer);

}
