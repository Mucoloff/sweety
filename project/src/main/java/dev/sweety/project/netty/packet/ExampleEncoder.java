package dev.sweety.project.netty.packet;

import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;

public class ExampleEncoder implements CallableEncoder<IExampleObj> {

    @Override
    public void write(PacketBuffer buffer, IExampleObj data) {
        buffer.writeVarInt(data.getValue());
        buffer.writeString(data.getText());
    }
}
