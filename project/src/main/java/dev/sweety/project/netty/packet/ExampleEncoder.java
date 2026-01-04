package dev.sweety.project.netty.packet;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;

public class ExampleEncoder implements CallableEncoder<ExampleObj> {

    @Override
    public void write(PacketBuffer buffer, ExampleObj data) {
        buffer.writeVarInt(data.getValue());
        buffer.writeString(data.getText());
    }
}
