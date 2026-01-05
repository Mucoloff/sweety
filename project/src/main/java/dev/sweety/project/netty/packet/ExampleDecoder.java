package dev.sweety.project.netty.packet;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableDecoder;

public class ExampleDecoder implements CallableDecoder<IExampleObj> {

    @Override
    public IExampleObj read(final PacketBuffer buffer) {
        return new ExampleObj(buffer.readVarInt(), buffer.readString());
    }
}
