package dev.sweety.project.netty.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

public class ExampleDecoder implements CallableDecoder<IExampleObj> {

    @Override
    public IExampleObj read(final BufferReader buffer) {
        return new ExampleObj(buffer.readVarInt(), buffer.readString());
    }
}
