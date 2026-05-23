package dev.sweety.project.netty.packet;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.netty.packet.buffer.io.callable.CallableDecoder;

public class ExampleDecoder implements CallableDecoder<ExampleObj> {

    @Override
    public ExampleObj read(final BufferReader buffer) {
        return new ExampleObjImpl(buffer.readVarInt(), buffer.readString());
    }
}
