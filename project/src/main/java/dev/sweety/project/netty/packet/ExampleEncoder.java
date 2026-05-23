package dev.sweety.project.netty.packet;

import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.callable.CallableEncoder;

public class ExampleEncoder implements CallableEncoder<ExampleObj> {

    @Override
    public void write(BufferWriter buffer, ExampleObj data) {
        buffer.writeVarInt(data.getValue());
        buffer.writeString(data.getText());
    }
}
